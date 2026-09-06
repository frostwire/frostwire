/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds an inline index manifest from unpublished {@link LocalIndex}
 * rows and publishes it as a BEP 46 mutable DHT item.
 *
 * <p>The manifest is a bencoded dictionary (§7.2 of the design doc):
 * <pre>
 * {
 *   "v":   1,
 *   "pub": "base64url raw Ed25519 pub",
 *   "rows": [ { "ih": "hex", "n": "name", "s": sizeBytes, "fc": fileCount } ... ],
 *   "ts":  epoch seconds
 * }
 * </pre>
 *
 * <p>Published under:
 * <ul>
 *   <li>key: 32-byte raw Ed25519 public key</li>
 *   <li>salt: {@link RelayConstants#BEP46_SALT_INDEX}</li>
 * </ul>
 *
 * <p>BEP 44 mutable items are capped at ~1000 bytes of bencoded
 * value. The publisher enforces {@link #MAX_MANIFEST_BYTES} and
 * includes as many rows as fit, sorted by most-recently-seen first.
 *
 * <p>Thread-safe: stateless aside from the monotonic sequence counter.
 */
public final class IndexAnnouncementPublisher {

    private static final Logger LOG = Logger.getLogger(IndexAnnouncementPublisher.class);

    /** BEP 44 mutable item value size limit (conservative). */
    static final int MAX_MANIFEST_BYTES = 950;

    static final int MANIFEST_VERSION = 1;

    private final LocalIndex index;
    private final IdentityKeys identity;
    private final ShareVisibilityPolicy visibility;
    private final Set<String> publishedHashes = new LinkedHashSet<>();
    private boolean published;
    private final AtomicLong seq = new AtomicLong(Instant.now().getEpochSecond());

    public IndexAnnouncementPublisher(LocalIndex index, IdentityKeys identity) {
        this(index, identity, null);
    }

    public IndexAnnouncementPublisher(LocalIndex index, IdentityKeys identity,
                                      ShareVisibilityPolicy visibility) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        this.index = index;
        this.identity = identity;
        this.visibility = visibility;
    }

    /**
     * Build and publish the index manifest. Returns the number of
     * rows included in the published manifest, or 0 if nothing was
     * published (empty index or DHT not available).
     */
    public synchronized int publishIfNeeded(SessionManager session) {
        if (session == null) {
            return 0;
        }
        try {
            long now = Instant.now().getEpochSecond();
            List<String> unpublished = index.needsRepublish(now,
                    RelayConstants.RELAY_REPUBLISH_INTERVAL_SEC);
            boolean withdrawn = false;
            for (String hash : publishedHashes) {
                if (!ShareVisibility.isPubliclyShared(hash, visibility) || index.get(hash).isEmpty()) {
                    withdrawn = true;
                    break;
                }
            }
            if (unpublished.isEmpty() && !withdrawn && published) {
                return 0;
            }

            // Gather the rows
            List<LocalSharedTorrent> rows = new ArrayList<>();
            Set<String> candidates = new LinkedHashSet<>(unpublished);
            candidates.addAll(publishedHashes);
            for (String infoHashHex : candidates) {
                if (ShareVisibility.isPubliclyShared(infoHashHex, visibility)) {
                    index.get(infoHashHex).ifPresent(rows::add);
                }
            }
            if (rows.isEmpty() && publishedHashes.isEmpty() && published) {
                return 0;
            }

            // Sort by last_seen descending (most recent first)
            rows.sort((a, b) -> Long.compare(b.lastSeenAt(), a.lastSeenAt()));

            // Build manifest, fitting within MAX_MANIFEST_BYTES
            Entry manifest = buildManifest(rows);
            if (manifest == null) {
                // Replace our last advertisement after the final public share is withdrawn.
                manifest = buildManifestEntry(java.util.Collections.emptyList());
            }

            // Publish as BEP 46 mutable item
            byte[] pubKey = identity.ed25519PubRaw();
            byte[] privKey = identity.ed25519SecretKeyNaCl();
            byte[] salt = RelayConstants.BEP46_SALT_INDEX.getBytes(StandardCharsets.US_ASCII);
            session.dhtPutItem(pubKey, privKey, manifest, salt);
            published = true;

            // Mark rows as published
            long pubTime = Instant.now().getEpochSecond();
            int count = 0;
            publishedHashes.clear();
            for (Entry row : manifest.dictionary().get("rows").list()) {
                String hash = row.dictionary().get("ih").string();
                publishedHashes.add(hash);
                index.markPublished(hash, pubTime);
                count++;
            }

            LOG.info("Published index manifest with " + count +
                    " row(s), seq=" + seq.get());
            return count;
        } catch (Throwable t) {
            LOG.warn("Failed to publish index manifest", t);
            return 0;
        }
    }

    /**
     * Build the bencoded manifest entry, truncating rows to fit within
     * {@link #MAX_MANIFEST_BYTES}.
     */
    Entry buildManifest(List<LocalSharedTorrent> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        // Grow only the bounded manifest prefix, not repeated full-catalog encodings.
        List<LocalSharedTorrent> included = new ArrayList<>();
        Entry last = null;
        for (LocalSharedTorrent row : rows) {
            if (row == null || !ShareVisibility.isPubliclyShared(row.infoHashHex(), visibility)) {
                continue;
            }
            included.add(row);
            Entry entry = buildManifestEntry(included);
            byte[] bencoded = entry.bencode();
            if (bencoded.length > MAX_MANIFEST_BYTES) {
                break;
            }
            last = entry;
        }
        return last;
    }

    private Entry buildManifestEntry(List<LocalSharedTorrent> rows) {
        List<Entry> rowEntries = new ArrayList<>(rows.size());
        for (LocalSharedTorrent t : rows) {
            Map<String, Object> rowMap = new TreeMap<>();
            rowMap.put("fc", new Entry((long) t.fileCount()));
            rowMap.put("ih", new Entry(t.infoHashHex()));
            rowMap.put("n", new Entry(t.name()));
            rowMap.put("s", new Entry(t.sizeBytes()));
            rowEntries.add(Entry.fromMap(rowMap));
        }

        Map<String, Object> map = new TreeMap<>();
        map.put("pub", new Entry(java.util.Base64.getEncoder()
                .withoutPadding().encodeToString(identity.ed25519PubRaw())));
        map.put("rows", Entry.fromList(rowEntries));
        map.put("ts", new Entry(Instant.now().getEpochSecond()));
        map.put("v", new Entry((long) MANIFEST_VERSION));
        return Entry.fromMap(map);
    }

}
