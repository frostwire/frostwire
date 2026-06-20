/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches a remote peer's shared torrent catalog from the DHT and
 * parses it into a list of {@link RemoteTorrentEntry}.
 *
 * <p>The transport is abstracted behind {@link IndexSource} (composition
 * over inheritance). The default DHT-backed source is {@link DhtIndexSource}.
 * Tests can inject a fake source to avoid spinning up a real DHT cluster.
 *
 * <p>The manifest format (published by {@link IndexAnnouncementPublisher}
 * as a BEP 46 mutable DHT item under {@link RelayConstants#BEP46_SALT_INDEX})
 * is a JSON object:
 * <pre>
 * { "v": 1, "pub": "base64url", "rows": [{"ih":"hex","n":"name","s":123,"fc":1}], "ts": 1700000000 }
 * </pre>
 *
 * <p><b>Caching:</b> the first call for a given peer triggers a remote
 * lookup. Subsequent calls return the cached entries until
 * {@link #evict(byte[])} is called or the cache is cleared. Peers
 * with no manifest (or malformed manifests) are cached as absent so
 * repeated lookups are skipped.
 *
 * <p>Fail-closed: any source or parse error returns an empty list.
 */
public final class RemoteIndexFetcher {

    private static final Logger LOG = Logger.getLogger(RemoteIndexFetcher.class);

    public static final int MANIFEST_VERSION = 1;

    private final IndexSource source;
    private final ConcurrentHashMap<String, List<RemoteTorrentEntry>> cached = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> absent = new ConcurrentHashMap<>();

    public RemoteIndexFetcher(IndexSource source) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        this.source = source;
    }

    /**
     * Fetch and parse a peer's catalog. Returns the list of entries,
     * or an empty list if the peer has no published catalog, the
     * lookup failed, or parsing failed.
     */
    public List<RemoteTorrentEntry> fetchCatalog(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return Collections.emptyList();
        }
        String key = Hex.encode(peerPub);
        List<RemoteTorrentEntry> cachedEntries = cached.get(key);
        if (cachedEntries != null) {
            return cachedEntries;
        }
        if (absent.containsKey(key)) {
            return Collections.emptyList();
        }
        try {
            Optional<byte[]> bytes = source.fetch(peerPub);
            if (bytes == null || !bytes.isPresent()) {
                absent.put(key, Boolean.TRUE);
                return Collections.emptyList();
            }
            List<RemoteTorrentEntry> entries = parseManifest(bytes.get());
            if (entries == null) {
                absent.put(key, Boolean.TRUE);
                return Collections.emptyList();
            }
            cached.put(key, entries);
            return entries;
        } catch (Throwable t) {
            LOG.debug("RemoteIndexFetcher failed for peer " + key, t);
            return Collections.emptyList();
        }
    }

    /**
     * Drop a peer from the cache. Forces the next fetchCatalog call
     * to re-query the source.
     */
    public void evict(byte[] peerPub) {
        if (peerPub == null) {
            return;
        }
        String key = Hex.encode(peerPub);
        cached.remove(key);
        absent.remove(key);
    }

    /** Clear all cached catalogs. */
    public void clear() {
        cached.clear();
        absent.clear();
    }

    /** Number of peers with cached catalogs. */
    public int cacheSize() {
        return cached.size();
    }

    // --- manifest parsing / building helpers ---

    /**
     * Parse the {@code rows} array from a JSON manifest. Returns null
     * on malformed JSON or missing required fields (internal signal
     * for cache-as-absent), an empty list for a valid manifest with
     * no rows, or the list of entries on success.
     */
    public static List<RemoteTorrentEntry> parseManifest(byte[] jsonBytes) {
        if (jsonBytes == null || jsonBytes.length == 0) {
            return Collections.emptyList();
        }
        try {
            JsonObject root = JsonParser.parseString(
                    new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement rowsElement = root.get("rows");
            if (rowsElement == null || !rowsElement.isJsonArray()) {
                return null;
            }
            JsonArray rows = rowsElement.getAsJsonArray();
            List<RemoteTorrentEntry> out = new ArrayList<>(rows.size());
            for (JsonElement rowEl : rows) {
                if (!rowEl.isJsonObject()) {
                    return null;
                }
                JsonObject row = rowEl.getAsJsonObject();
                JsonElement ih = row.get("ih");
                JsonElement n = row.get("n");
                JsonElement s = row.get("s");
                JsonElement fc = row.get("fc");
                if (ih == null || n == null || s == null || fc == null) {
                    return null;
                }
                out.add(new RemoteTorrentEntry(
                        ih.getAsString(),
                        n.getAsString(),
                        s.getAsLong(),
                        fc.getAsInt()));
            }
            return Collections.unmodifiableList(out);
        } catch (Throwable t) {
            LOG.debug("parseManifest failed", t);
            return null;
        }
    }

    /**
     * Compute the canonical signing bytes for a manifest. The signature
     * in a catalog browse response is over these bytes.
     *
     * <p>Layout: {@code v(4) | pub_len(4) | pub | ts(8) | rowsHash(32)}
     * where {@code rowsHash} is SHA-256 of the concatenation of each
     * row's canonical bytes: {@code ih_len(4) | ih | n_len(4) | n | s(8) | fc(4)}.
     */
    public static byte[] manifestCanonicalBytes(int version, String pubB64,
                                                 long timestamp,
                                                 List<RemoteTorrentEntry> rows) {
        try {
            byte[] rowsHash = sha256(rowsCanonicalBytes(rows));
            byte[] pubBytes = pubB64.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buf = ByteBuffer.allocate(
                    4                              // version
                            + 4 + pubBytes.length   // pub
                            + 8                      // timestamp
                            + 32                     // rowsHash
            );
            buf.putInt(version);
            buf.putInt(pubBytes.length);
            buf.put(pubBytes);
            buf.putLong(timestamp);
            buf.put(rowsHash);
            return buf.array();
        } catch (Exception e) {
            throw new IllegalStateException("manifestCanonicalBytes failed", e);
        }
    }

    /**
     * Build a signed JSON manifest suitable for sending as a catalog
     * browse response. The {@code signature} is included as a base64url
     * {@code sig} field.
     */
    public static byte[] buildManifestJson(int version, String pubB64,
                                           long timestamp,
                                           List<RemoteTorrentEntry> rows,
                                           byte[] signature) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("v", version);
        root.put("pub", pubB64);
        List<Map<String, Object>> rowMaps = new ArrayList<>(rows.size());
        for (RemoteTorrentEntry r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ih", r.infoHashHex());
            row.put("n", r.name());
            row.put("s", r.sizeBytes());
            row.put("fc", (long) r.fileCount());
            rowMaps.add(row);
        }
        root.put("rows", rowMaps);
        root.put("ts", timestamp);
        root.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signature));
        return new Gson().toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] rowsCanonicalBytes(List<RemoteTorrentEntry> rows) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (RemoteTorrentEntry r : rows) {
            byte[] ih = r.infoHashHex().getBytes(StandardCharsets.UTF_8);
            byte[] n = r.name().getBytes(StandardCharsets.UTF_8);
            byte[] rowBuf = new byte[4 + ih.length + 4 + n.length + 8 + 4];
            ByteBuffer buf = ByteBuffer.wrap(rowBuf);
            buf.putInt(ih.length);
            buf.put(ih);
            buf.putInt(n.length);
            buf.put(n);
            buf.putLong(r.sizeBytes());
            buf.putInt(r.fileCount());
            out.write(rowBuf, 0, rowBuf.length);
        }
        return out.toByteArray();
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    // --- IndexSource interface ---

    /**
     * Pluggable transport for fetching a remote peer's index manifest.
     * The default DHT-backed source is {@link DhtIndexSource}. Tests can
     * substitute a fake source to avoid spinning up a real DHT cluster.
     */
    public interface IndexSource {

        /**
         * Fetch the manifest bytes for {@code peerPub}, or empty if the
         * peer has no published catalog or the lookup failed.
         */
        Optional<byte[]> fetch(byte[] peerPub);
    }

    // --- DhtIndexSource ---

    /**
     * BEP 46 mutable DHT lookup for index manifests. Calls
     * {@link SessionManager#dhtGetItem(byte[], byte[], int)} with the
     * peer's raw Ed25519 public key and
     * {@link RelayConstants#BEP46_SALT_INDEX}. jlibtorrent verifies the
     * publisher signature on the returned item; we convert the bencoded
     * {@link Entry} to JSON bytes for downstream Gson parsing.
     */
    public static final class DhtIndexSource implements IndexSource {

        private static final int DEFAULT_DHT_TIMEOUT_MS = 5000;

        private final SessionManager session;
        private final int dhtTimeoutMs;

        public DhtIndexSource(SessionManager session) {
            this(session, DEFAULT_DHT_TIMEOUT_MS);
        }

        public DhtIndexSource(SessionManager session, int dhtTimeoutMs) {
            if (session == null) {
                throw new IllegalArgumentException("session is null");
            }
            if (dhtTimeoutMs <= 0) {
                throw new IllegalArgumentException("dhtTimeoutMs must be > 0");
            }
            this.session = session;
            this.dhtTimeoutMs = dhtTimeoutMs;
        }

        @Override
        public Optional<byte[]> fetch(byte[] peerPub) {
            if (peerPub == null || peerPub.length != 32) {
                return Optional.empty();
            }
            try {
                byte[] salt = RelayConstants.BEP46_SALT_INDEX
                        .getBytes(StandardCharsets.US_ASCII);
                SessionManager.MutableItem item = session.dhtGetItem(peerPub, salt, dhtTimeoutMs);
                if (item == null || item.item == null) {
                    return Optional.empty();
                }
                byte[] jsonBytes = entryToJsonBytes(item.item);
                return Optional.of(jsonBytes);
            } catch (Throwable t) {
                LOG.debug("DhtIndexSource fetch failed for peer " +
                        Hex.encode(peerPub), t);
                return Optional.empty();
            }
        }

        private static byte[] entryToJsonBytes(Entry entry) {
            Map<String, Object> map = new LinkedHashMap<>();
            Map<String, Entry> dict = entry.dictionary();
            for (String key : dict.keySet()) {
                map.put(key, entryToObject(dict.get(key)));
            }
            return new Gson().toJson(map).getBytes(StandardCharsets.UTF_8);
        }

        @SuppressWarnings("unchecked")
        private static Object entryToObject(Entry e) {
            if (e == null) return null;
            try {
                List<Object> list = new ArrayList<>();
                for (Entry child : e.list()) {
                    list.add(entryToObject(child));
                }
                return list;
            } catch (Throwable notList) {
                try {
                    Map<String, Entry> d = e.dictionary();
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (String key : d.keySet()) {
                        m.put(key, entryToObject(d.get(key)));
                    }
                    return m;
                } catch (Throwable notDict) {
                    try {
                        return e.integer();
                    } catch (Throwable notInt) {
                        try {
                            return e.string();
                        } catch (Throwable t) {
                            return null;
                        }
                    }
                }
            }
        }
    }

    // --- RemoteTorrentEntry ---

    /**
     * Immutable catalog entry parsed from a remote peer's index manifest.
     */
    public static final class RemoteTorrentEntry {

        private final String infoHashHex;
        private final String name;
        private final long sizeBytes;
        private final int fileCount;

        public RemoteTorrentEntry(String infoHashHex, String name,
                                  long sizeBytes, int fileCount) {
            this.infoHashHex = infoHashHex;
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.fileCount = fileCount;
        }

        public String infoHashHex() {
            return infoHashHex;
        }

        public String name() {
            return name;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public int fileCount() {
            return fileCount;
        }

        @Override
        public String toString() {
            return "RemoteTorrentEntry{ih=" + infoHashHex +
                    ", n='" + name + '\'' +
                    ", s=" + sizeBytes +
                    ", fc=" + fileCount + '}';
        }
    }
}
