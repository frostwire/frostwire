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
 * host:port,ROLE,lastSuccessfulPingMs
 * 1.2.3.4:6888,BOTH,1712345678900
 * relay.example.com:6888,FORWARDER,1712345678900
 * </pre>
 *
 * <p>Only entries that have successfully pinged (via identity handshake)
 * are considered "live" for display. Failed pings do not remove entries
 * (they are maintained for retry / future bootstrapping).
 */
public final class IceBridgeHostCache {

    private static final Logger LOG = Logger.getLogger(IceBridgeHostCache.class);

    private static final String DEFAULT_FILE_NAME = "icebridge_host_cache.txt";

    private final File cacheFile;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final OutgoingRelayClient pingClient;

    private static volatile IceBridgeHostCache INSTANCE;

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

    private static File defaultCacheFile() {
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
            return new Entry(host, port, role, lastSuccess);
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
                w.write("# IceBridge host cache - host:port,ROLE,lastSuccessfulPingMs\n");
                for (Entry e : entries) {
                    String role = (e.role != null) ? e.role : "";
                    w.write(e.host + ":" + e.port + "," + role + "," + e.lastSuccessfulPingMs + "\n");
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to save IceBridge host cache: " + cacheFile, e);
        }
    }

    /**
     * Add or update a known relay host. If it was previously known we keep the
     * most recent success timestamp (unless a newer one is provided).
     */
    public synchronized void addOrUpdate(String host, int port, String role) {
        if (host == null || host.isEmpty() || port <= 0) return;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.host.equals(host) && e.port == port) {
                String newRole = (role != null && !role.isEmpty()) ? role : e.role;
                long ts = e.lastSuccessfulPingMs; // keep existing success time
                entries.set(i, new Entry(host, port, newRole, ts));
                save();
                return;
            }
        }
        entries.add(new Entry(host, port, role, 0));
        save();
    }

    /** Mark a successful ping for the given host (updates timestamp and role). */
    public synchronized void markSuccess(String host, int port, String role) {
        if (host == null || host.isEmpty() || port <= 0) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.host.equals(host) && e.port == port) {
                String newRole = (role != null && !role.isEmpty()) ? role : e.role;
                entries.set(i, new Entry(host, port, newRole, now));
                save();
                return;
            }
        }
        entries.add(new Entry(host, port, role, now));
        save();
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
     * Attempt to ping every known entry. Successful pings update lastSuccessfulPingMs
     * and role (if obtained). Failures leave the entry in the cache (for retry later).
     * This is blocking; call from a background thread.
     */
    public void refreshPings() {
        List<Entry> snapshot = new ArrayList<>(entries);
        for (Entry e : snapshot) {
            try {
                Optional<IdentityRecord> rec = pingClient.fetchIdentity(e.host, e.port);
                if (rec.isPresent()) {
                    IdentityRecord r = rec.get();
                    if (r.verifySignature()) {
                        markSuccess(e.host, e.port, r.role());
                        continue;
                    }
                }
                // ping failed or bad sig: keep entry, do not update success time
            } catch (Exception ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.contains("invalid frame length")) {
                    LOG.debug("IceBridge host ping: " + e.host + ":" + e.port +
                            " does not speak the relay protocol (stale entry?)");
                } else {
                    LOG.debug("Ping failed for IceBridge host " + e.host + ":" + e.port, ex);
                }
            }
        }
    }

    public static final class Entry {
        public final String host;
        public final int port;
        public final String role; // "BOTH", "FORWARDER", "CLIENT", or null
        public final long lastSuccessfulPingMs;

        public Entry(String host, int port, String role, long lastSuccessfulPingMs) {
            this.host = host;
            this.port = port;
            this.role = role;
            this.lastSuccessfulPingMs = lastSuccessfulPingMs;
        }

        @Override
        public String toString() {
            return host + ":" + port + (role != null ? " (" + role + ")" : "");
        }
    }
}
