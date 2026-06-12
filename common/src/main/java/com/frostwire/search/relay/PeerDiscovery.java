/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers other FrostWire nodes on the DHT and registers them
 * in the local {@link PeerDirectory} so they can be queried
 * later via the relay protocol.
 *
 * <p><b>Discovery flow:</b>
 * <ol>
 *   <li>The source's {@link PeerDiscoverySource#fetchEndpoints}
 *       returns a list of {@code (host, port)} candidates.</li>
 *   <li>For each candidate, we register it in the
 *       {@link PeerDirectory} with a placeholder pubkey derived
 *       from {@code SHA-256(host:port)}. The placeholder is a
 *       stable 32-byte identifier that uniquely maps to the
 *       endpoint.</li>
 *   <li>When the peer actually contacts us (sends a relay
 *       request), we learn their real pubkey from the request
 *       signature. Callers can then call
 *       {@link PeerDirectory#evict} on the placeholder and
 *       {@link PeerDirectory#upsert} the real pubkey to
 *       upgrade the entry.</li>
 * </ol>
 *
 * <p><b>Identity record fetch:</b>
 * {@link #fetchIdentityRecord} wraps the source's
 * {@link PeerDiscoverySource#fetchIdentityEntry} for a known
 * pubkey. This is the entry point for the future
 * "learn-the-peer's-identity" step, once we have the pubkey
 * (from a request signature or another out-of-band channel).
 *
 * <p><b>Fail-closed:</b> any source error returns an empty list
 * or null.
 */
public final class PeerDiscovery {

    private static final Logger LOG = Logger.getLogger(PeerDiscovery.class);

    /** BEP 46 lookup timeout for identity records. */
    public static final int DEFAULT_IDENTITY_TIMEOUT_MS = 5000;

    private final PeerDiscoverySource source;
    private final PeerDirectory directory;

    public PeerDiscovery(PeerDiscoverySource source, PeerDirectory directory) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        this.source = source;
        this.directory = directory;
    }

    /**
     * Run one discovery pass. Returns the list of endpoints
     * that were newly registered in the directory (i.e., not
     * already known).
     */
    public List<DiscoveredEndpoint> discoverAndRegister() {
        List<DiscoveredEndpoint> discovered = new ArrayList<>();
        try {
            List<DiscoveredEndpoint> endpoints = source.fetchEndpoints();
            for (DiscoveredEndpoint ep : endpoints) {
                String host = ep.host;
                int port = ep.port;
                if (host == null || host.isEmpty() || port <= 0) {
                    continue;
                }
                byte[] placeholderPubkey = placeholderPubkey(host, port);
                PeerDirectory.PeerInfo existing =
                        directory.get(placeholderPubkey).orElse(null);
                if (existing != null
                        && existing.hostname().equals(host)
                        && existing.utpPort() == port) {
                    continue;
                }
                directory.upsert(placeholderPubkey, host, port);
                discovered.add(new DiscoveredEndpoint(host, port));
            }
        } catch (Throwable t) {
            LOG.debug("Peer discovery failed", t);
        }
        return discovered;
    }

    /**
     * Fetch the BEP 46 identity record for a known pubkey.
     * Returns null on any failure.
     */
    public IdentityRecord fetchIdentityRecord(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return null;
        }
        try {
            Entry entry = source.fetchIdentityEntry(peerPub);
            if (entry == null) {
                return null;
            }
            return IdentityRecord.fromEntry(entry);
        } catch (Throwable t) {
            LOG.debug("Identity record fetch failed for " +
                    com.frostwire.util.Hex.encode(peerPub), t);
            return null;
        }
    }

    /**
     * Derive a stable 32-byte placeholder pubkey from an
     * endpoint. The placeholder is NOT a real Ed25519 pubkey;
     * it just gives the directory a unique key per endpoint.
     */
    public static byte[] placeholderPubkey(String host, int port) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(host.getBytes(StandardCharsets.UTF_8));
            sha256.update((byte) ':');
            sha256.update(Integer.toString(port).getBytes(StandardCharsets.UTF_8));
            return sha256.digest();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
