/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.OutgoingRelayClient;
import com.frostwire.util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple persistent cache of known IceBridge relay hosts (for bootstrapping
 * and for display in settings).
 *
 * <p>Format (text, one entry per line):
 * <pre>
 * # icebridge host cache
 * host:port,ROLE,lastSuccessfulPingMs,consecutiveFailures
 * 1.2.3.4:6888,BOTH,1712345678900,0
 * relay.example.com:6888,FORWARDER,1712345678900,2
 * </pre>
 *
 * <p>Only entries that have successfully pinged (via identity handshake)
 * are considered "live" for display. Entries that fail
 * {@link #MAX_CONSECUTIVE_FAILURES} consecutive pings are evicted so dead
 * hosts (and our own un-hairpinnable external IP) stop being retried every
 * discovery tick. Any success resets the streak.
 */
public final class IceBridgeHostCache {

    private static final Logger LOG = Logger.getLogger(IceBridgeHostCache.class);

    private static final String DEFAULT_FILE_NAME = "icebridge_host_cache.txt";

    /**
     * Consecutive ping failures after which a dead entry is evicted. Success
     * resets the streak, so a brief outage (reboot, redeploy) never evicts a
     * live host: at most ~1 strike accrues per discovery tick.
     */
    public static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final File cacheFile;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final OutgoingRelayClient pingClient;

    private static volatile IceBridgeHostCache INSTANCE;
    /** Optional platform path (Android: libtorrent home). Set before {@link #getInstance()}. */
    private static volatile File configuredCacheFile;

    /**
     * Point the singleton at a specific cache file (e.g. Android app-private
     * {@code files/libtorrent/icebridge_host_cache.txt}). Reloads if the path
     * changes. Call early during relay-stack startup.
     */
    public static void configure(File cacheFile) {
        if (cacheFile == null) {
            throw new IllegalArgumentException("cacheFile is null");
        }
        synchronized (IceBridgeHostCache.class) {
            configuredCacheFile = cacheFile;
            if (INSTANCE != null
                    && !INSTANCE.cacheFile.getAbsolutePath().equals(cacheFile.getAbsolutePath())) {
                INSTANCE = new IceBridgeHostCache(cacheFile);
            }
        }
    }

    public static IceBridgeHostCache getInstance() {
        if (INSTANCE == null) {
            synchronized (IceBridgeHostCache.class) {
                if (INSTANCE == null) {
                    INSTANCE = new IceBridgeHostCache();
                }
            }
        }
        return INSTANCE;
    }

    public IceBridgeHostCache() {
        this(defaultCacheFile(), new OutgoingRelayClient());
    }

    public IceBridgeHostCache(File cacheFile) {
        this(cacheFile, new OutgoingRelayClient());
    }

    public IceBridgeHostCache(File cacheFile, OutgoingRelayClient pingClient) {
        if (cacheFile == null) {
            throw new IllegalArgumentException("cacheFile is null");
        }
        this.cacheFile = cacheFile;
        // Use relatively short timeouts for host cache pings — we expect many entries
        // to be stale or non-FrostWire nodes.
        this.pingClient = pingClient != null ? pingClient
                : new OutgoingRelayClient(2000, 3000);
        load();
    }

    /** Absolute path of the cache file (for settings UI). */
    public File cacheFile() {
        return cacheFile;
    }

    private static File defaultCacheFile() {
        File configured = configuredCacheFile;
        if (configured != null) {
            return configured;
        }
        File dir = new File(System.getProperty("user.home"), ".frostwire");
        if (!dir.exists()) {
            // best effort
            dir.mkdirs();
        }
        return new File(dir, DEFAULT_FILE_NAME);
    }

    public synchronized void load() {
        entries.clear();
        if (!cacheFile.exists()) {
            return;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Entry e = parseLine(line);
                if (e != null) {
                    entries.add(e);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load IceBridge host cache: " + cacheFile, e);
        }
    }

    private static Entry parseLine(String line) {
        try {
            // host:port,ROLE, timestamp
            String[] parts = line.split(",", -1);
            if (parts.length < 2) return null;
            String hp = parts[0].trim();
            int colon = hp.lastIndexOf(':');
            if (colon <= 0) return null;
            String host = hp.substring(0, colon).trim();
            int port = Integer.parseInt(hp.substring(colon + 1).trim());
            String role = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : null;
            long lastSuccess = 0;
            if (parts.length > 2) {
                try { lastSuccess = Long.parseLong(parts[2].trim()); } catch (NumberFormatException ignored) {}
            }
            int failures = 0;
            if (parts.length > 3) {
                try { failures = Integer.parseInt(parts[3].trim()); } catch (NumberFormatException ignored) {}
                if (failures < 0) failures = 0;
            }
            return new Entry(host, port, role, lastSuccess, failures);
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void save() {
        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8))) {
                w.write("# IceBridge host cache - host:port,ROLE,lastSuccessfulPingMs,consecutiveFailures\n");
                for (Entry e : entries) {
                    String role = (e.role != null) ? e.role : "";
                    w.write(e.host + ":" + e.port + "," + role + "," + e.lastSuccessfulPingMs
                            + "," + e.consecutiveFailures + "\n");
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to save IceBridge host cache: " + cacheFile, e);
        }
    }

    /**
     * Add or update a known relay host. If it was previously known we keep the
     * most recent success timestamp and failure streak (unless a newer success
     * is provided via {@link #markSuccess}).
     */
    public synchronized void addOrUpdate(String host, int port, String role) {
        if (host == null || host.isEmpty() || port <= 0) return;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.host.equals(host) && e.port == port) {
                String newRole = (role != null && !role.isEmpty()) ? role : e.role;
                long ts = e.lastSuccessfulPingMs; // keep existing success time
                entries.set(i, new Entry(host, port, newRole, ts, e.consecutiveFailures));
                save();
                return;
            }
        }
        entries.add(new Entry(host, port, role, 0, 0));
        save();
    }

    /** Mark a successful ping for the given host (updates timestamp and role, resets failures). */
    public synchronized void markSuccess(String host, int port, String role) {
        if (host == null || host.isEmpty() || port <= 0) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.host.equals(host) && e.port == port) {
                String newRole = (role != null && !role.isEmpty()) ? role : e.role;
                entries.set(i, new Entry(host, port, newRole, now, 0));
                save();
                return;
            }
        }
        entries.add(new Entry(host, port, role, now, 0));
        save();
    }

    /**
     * Record one failed ping for a known host. Unknown hosts are ignored
     * (never cache pure failures). At {@link #MAX_CONSECUTIVE_FAILURES} the
     * entry is evicted so discovery stops retrying corpses.
     *
     * @return true when this failure evicted the entry
     */
    public synchronized boolean markFailure(String host, int port) {
        if (host == null || host.isEmpty() || port <= 0) return false;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.host.equals(host) && e.port == port) {
                int failures = e.consecutiveFailures + 1;
                if (failures >= MAX_CONSECUTIVE_FAILURES) {
                    entries.remove(i);
                    LOG.info("IceBridge host cache: evicting " + host + ":" + port
                            + " after " + failures + " consecutive failures");
                } else {
                    entries.set(i, new Entry(host, port, e.role,
                            e.lastSuccessfulPingMs, failures));
                }
                save();
                return failures >= MAX_CONSECUTIVE_FAILURES;
            }
        }
        return false;
    }

    public List<Entry> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Returns entries that have successfully pinged at least once (and optionally within a window).
     * If withinMs <= 0, returns all that ever succeeded.
     */
    public List<Entry> getPingable(long withinMs) {
        long cutoff = (withinMs > 0) ? (System.currentTimeMillis() - withinMs) : 0;
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.lastSuccessfulPingMs > cutoff) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Attempt to ping every known entry via TCP identity handshake on the
     * relay/identity port (default 6888). This is <em>not</em> the IceBridge
     * control HTTP API and is not mesh TELEMETRY — operators watching a pure
     * FORWARDER should look for {@code IceBridge identity handshake OK} on the
     * identity TCP listener.
     *
     * <p>Successful pings update lastSuccessfulPingMs and role. Failed pings
     * accrue a consecutive-failure streak ({@link #markFailure}) and evict
     * after {@link #MAX_CONSECUTIVE_FAILURES}. Blocking; call from a
     * background thread.
     */
    public void refreshPings() {
        List<Entry> snapshot = new ArrayList<>(entries);
        if (snapshot.isEmpty()) {
            LOG.info("IceBridge host refresh: cache empty (nothing to TCP-ping)");
            return;
        }
        int ok = 0;
        int fail = 0;
        LOG.info("IceBridge host refresh: TCP identity ping of " + snapshot.size() + " host(s)");
        for (Entry e : snapshot) {
            try {
                Optional<IdentityRecord> rec = pingClient.fetchIdentity(e.host, e.port);
                if (rec.isPresent()) {
                    IdentityRecord r = rec.get();
                    if (r.verifySignature()) {
                        markSuccess(e.host, e.port, r.role());
                        ok++;
                        LOG.info("IceBridge host ping OK " + e.host + ":" + e.port
                                + " role=" + (r.role() != null ? r.role() : "?"));
                        continue;
                    }
                    fail++;
                    markFailure(e.host, e.port);
                    LOG.warn("IceBridge host ping bad signature " + e.host + ":" + e.port);
                } else {
                    fail++;
                    markFailure(e.host, e.port);
                    LOG.warn("IceBridge host ping failed " + e.host + ":" + e.port
                            + " (no identity record)");
                }
            } catch (Exception ex) {
                fail++;
                markFailure(e.host, e.port);
                String msg = ex.getMessage();
                if (msg != null && msg.contains("invalid frame length")) {
                    LOG.warn("IceBridge host ping: " + e.host + ":" + e.port
                            + " does not speak the relay protocol (stale entry?)");
                } else {
                    LOG.warn("IceBridge host ping failed " + e.host + ":" + e.port
                            + ": " + (msg != null ? msg : ex.getClass().getSimpleName()));
                }
            }
        }
        LOG.info("IceBridge host refresh done: ok=" + ok + " fail=" + fail
                + " total=" + snapshot.size());
    }

    /**
     * Optional second half of "Refresh / Ping" for USE_REMOTE / in-process
     * clients that have an {@link com.frostwire.search.relay.icebridge.client.IceBridgeClient}:
     * control {@code /health} plus mesh {@link MeshProtocolId#TELEMETRY} PING
     * (single-byte {@code 0x01}) to every looked-up peer. That is what produces
     * {@code TELEMETRY PING} lines on a standalone forwarder's control/mesh logs.
     *
     * @param client live control client, or null to no-op
     */
    public void refreshMeshTelemetry(
            com.frostwire.search.relay.icebridge.client.IceBridgeClient client) {
        if (client == null) {
            LOG.info("IceBridge mesh refresh: no control client (stack not running?)");
            return;
        }
        try {
            if (!client.health()) {
                LOG.warn("IceBridge mesh refresh: control /health failed");
                return;
            }
            LOG.info("IceBridge mesh refresh: control /health OK");
            java.util.List<com.frostwire.search.relay.icebridge.control.PeerInfo> peers =
                    client.lookup(50);
            if (peers == null || peers.isEmpty()) {
                LOG.info("IceBridge mesh refresh: lookup empty (no registered peers yet)");
                return;
            }
            int sent = 0;
            int failed = 0;
            byte[] ping = new byte[]{0x01};
            for (com.frostwire.search.relay.icebridge.control.PeerInfo info : peers) {
                if (info == null || info.pub == null || info.pub.isEmpty()) {
                    continue;
                }
                byte[] pub;
                try {
                    pub = java.util.Base64.getUrlDecoder().decode(info.pub);
                } catch (IllegalArgumentException e) {
                    try {
                        pub = java.util.Base64.getDecoder().decode(info.pub);
                    } catch (IllegalArgumentException e2) {
                        failed++;
                        continue;
                    }
                }
                if (pub.length != 32) {
                    failed++;
                    continue;
                }
                if (client.send(pub, MeshProtocolId.TELEMETRY, ping)) {
                    sent++;
                } else {
                    failed++;
                }
            }
            LOG.info("IceBridge mesh refresh: TELEMETRY PING sent=" + sent
                    + " failed=" + failed + " peers=" + peers.size());
        } catch (Throwable t) {
            LOG.warn("IceBridge mesh refresh failed", t);
        }
    }

    public static final class Entry {
        public final String host;
        public final int port;
        public final String role; // "BOTH", "FORWARDER", "CLIENT", or null
        public final long lastSuccessfulPingMs;
        public final int consecutiveFailures;

        public Entry(String host, int port, String role, long lastSuccessfulPingMs) {
            this(host, port, role, lastSuccessfulPingMs, 0);
        }

        public Entry(String host, int port, String role, long lastSuccessfulPingMs, int consecutiveFailures) {
            this.host = host;
            this.port = port;
            this.role = role;
            this.lastSuccessfulPingMs = lastSuccessfulPingMs;
            this.consecutiveFailures = Math.max(0, consecutiveFailures);
        }

        @Override
        public String toString() {
            return host + ":" + port + (role != null ? " (" + role + ")" : "");
        }
    }
}
