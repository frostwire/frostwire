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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Discovers other FrostWire nodes on the DHT and registers them
 * in the local {@link PeerDirectory} so they can be queried
 * later via the relay protocol.
 *
 * <p><b>Discovery flow:</b>
 * <ol>
 *   <li>The source's {@link PeerDiscoverySource#fetchEndpoints}
 *       returns a list of {@code (host, port)} candidates.</li>
 *   <li>If a {@link PeerAuthenticator} is configured, each
 *       candidate is authenticated before it is registered. On
 *       success, the peer is inserted with its real Ed25519
 *       pubkey via {@link PeerDirectory#upsertVerified(byte[], String, int)}.
 *       On failure, the endpoint is dropped.</li>
 *   <li>When no authenticator is configured, each candidate is
 *       registered with a placeholder pubkey derived from
 *       {@code SHA-256(host:port)}. Placeholder entries are
 *       unverified and must not be used for distributed search.</li>
 * </ol>
 *
 * <p><b>Identity record fetch:</b>
 * {@link #fetchIdentityRecord} wraps the source's
 * {@link PeerDiscoverySource#fetchIdentityEntry} for a known
 * pubkey.
 *
 * <p><b>Fail-closed:</b> any source error returns an empty list
 * or null.
 */
public final class PeerDiscovery {

    private static final Logger LOG = Logger.getLogger(PeerDiscovery.class);

    /** BEP 46 lookup timeout for identity records. */
    public static final int DEFAULT_IDENTITY_TIMEOUT_MS = 5000;
    public static final int MAX_CANDIDATES_PER_PASS = 64;
    public static final int DISCOVERY_PASS_TIMEOUT_MS = 15_000;
    /** Identity handshakes in flight at once during a discovery pass. */
    public static final int DEFAULT_PROBE_CONCURRENCY = 8;

    private final PeerDiscoverySource source;
    private final PeerDirectory directory;
    private final PeerAuthenticator authenticator;
    private final byte[] ownEd25519Pub;
    private final AtomicBoolean discovering = new AtomicBoolean();
    private final java.util.concurrent.ConcurrentHashMap<String, Backoff> backoff =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Random random = new java.util.Random();
    private volatile int probeConcurrency = DEFAULT_PROBE_CONCURRENCY;
    private volatile int passTimeoutMs = DISCOVERY_PASS_TIMEOUT_MS;
    private volatile LongSupplier clockMs = System::currentTimeMillis;
    /**
     * Our currently-visible external IP supplier (e.g. BTEngine's latest
     * external_ip alert) plus our own relay port, for hairpin self-skip.
     * Evaluated per candidate so carrier rebinds are picked up. Unset = off.
     */
    private volatile Supplier<String> externalIpSupplier;
    private volatile int selfRelayPort;

    public PeerDiscovery(PeerDiscoverySource source, PeerDirectory directory) {
        this(source, directory, null, null);
    }

    public PeerDiscovery(PeerDiscoverySource source, PeerDirectory directory,
                         PeerAuthenticator authenticator) {
        this(source, directory, authenticator, null);
    }

    public PeerDiscovery(PeerDiscoverySource source, PeerDirectory directory,
                         PeerAuthenticator authenticator, byte[] ownEd25519Pub) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        this.source = source;
        this.directory = directory;
        this.authenticator = authenticator;
        this.ownEd25519Pub = (ownEd25519Pub != null) ? ownEd25519Pub.clone() : null;
    }

    /**
     * Configure hairpin self-skip: candidates matching our currently-visible
     * external IP *and* our own relay port are skipped without a handshake.
     * A candidate at our carrier-NAT public IP is either ourselves (hairpin
     * always fails) or a carrier-mate we cannot reach directly anyway; a mate
     * on a different port is still tried. The supplier is read per candidate
     * so IP rebinds take effect immediately. Pass a null supplier to disable.
     */
    public void setSelfEndpoint(Supplier<String> externalIpSupplier, int selfRelayPort) {
        this.externalIpSupplier = externalIpSupplier;
        this.selfRelayPort = selfRelayPort;
    }

    /**
     * Run one discovery pass. Returns the list of endpoints
     * that were newly registered in the directory (i.e., not
     * already known).
     *
     * <p>When an authenticator is present, only successfully
     * authenticated endpoints are returned and registered.
     *
     * <p>Candidates are probed in parallel (see {@link #setProbeConcurrency(int)}) so a handful of
     * unreachable peers cannot use up the pass; {@link DiscoveredEndpoint#preferred} endpoints go
     * first, and endpoints that fail are not retried until their back-off expires, so successive
     * passes work through a long candidate list instead of re-probing the same dead heads.
     */
    public List<DiscoveredEndpoint> discoverAndRegister() {
        List<DiscoveredEndpoint> discovered = new ArrayList<>();
        if (Thread.currentThread().isInterrupted()) return discovered;
        if (!discovering.compareAndSet(false, true)) return discovered;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(passTimeoutMs);
        try {
            List<DiscoveredEndpoint> candidates = selectCandidates(source.fetchEndpoints());
            if (authenticator == null) {
                registerPlaceholders(candidates, discovered);
            } else {
                probeAndRegister(candidates, deadline, discovered);
            }
        } catch (Throwable t) {
            LOG.debug("Peer discovery failed", t);
        } finally {
            discovering.set(false);
        }
        return discovered;
    }

    /** Valid, unique, probe-worthy candidates: preferred first, the rest shuffled, bounded. */
    private List<DiscoveredEndpoint> selectCandidates(List<DiscoveredEndpoint> endpoints) {
        java.util.Map<String, DiscoveredEndpoint> unique = new java.util.LinkedHashMap<>();
        if (endpoints != null) {
            for (DiscoveredEndpoint ep : endpoints) {
                if (ep == null) continue;
                String host = ep.host;
                int port = ep.port;
                if (host == null || host.isEmpty() || port <= 0 || port > 65535) continue;
                String key = host + ":" + port;
                DiscoveredEndpoint known = unique.get(key);
                if (known == null || (ep.preferred && !known.preferred)) {
                    unique.put(key, ep);
                }
            }
        }
        long now = clockMs.getAsLong();
        List<DiscoveredEndpoint> preferred = new ArrayList<>();
        List<DiscoveredEndpoint> others = new ArrayList<>();
        for (DiscoveredEndpoint ep : unique.values()) {
            if (isLocalEndpoint(ep.host)) {
                LOG.debug("Skipping likely local/self endpoint " + ep.host + ":" + ep.port);
                continue;
            }
            if (isSelfHairpin(ep.host, ep.port)) {
                LOG.debug("Skipping self hairpin endpoint " + ep.host + ":" + ep.port);
                continue;
            }
            if (isBackingOff(ep, now)) continue;
            (ep.preferred ? preferred : others).add(ep);
        }
        // Each pass looks at a different slice of a crowded topic, instead of the same head.
        java.util.Collections.shuffle(others, random);
        List<DiscoveredEndpoint> ordered = new ArrayList<>(preferred);
        ordered.addAll(others);
        if (ordered.size() > MAX_CANDIDATES_PER_PASS) {
            return new ArrayList<>(ordered.subList(0, MAX_CANDIDATES_PER_PASS));
        }
        return ordered;
    }

    private void registerPlaceholders(List<DiscoveredEndpoint> candidates,
                                      List<DiscoveredEndpoint> discovered) {
        for (DiscoveredEndpoint ep : candidates) {
            byte[] placeholderPubkey = placeholderPubkey(ep.host, ep.port);
            if (alreadyKnown(placeholderPubkey, ep.host, ep.port)) {
                continue;
            }
            directory.upsert(placeholderPubkey, ep.host, ep.port);
            discovered.add(new DiscoveredEndpoint(ep.host, ep.port));
        }
    }

    private void probeAndRegister(List<DiscoveredEndpoint> candidates, long deadline,
                                  List<DiscoveredEndpoint> discovered) throws InterruptedException {
        if (candidates.isEmpty()) return;
        int threads = Math.max(1, Math.min(probeConcurrency, candidates.size()));
        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
                    Thread thread = new Thread(r, "peer-discovery-probe");
                    thread.setDaemon(true);
                    return thread;
                });
        java.util.concurrent.ExecutorCompletionService<Probe> completions =
                new java.util.concurrent.ExecutorCompletionService<>(pool);
        java.util.Map<java.util.concurrent.Future<Probe>, DiscoveredEndpoint> pending =
                new java.util.IdentityHashMap<>();
        try {
            for (DiscoveredEndpoint ep : candidates) {
                pending.put(completions.submit(() -> probe(ep, deadline)), ep);
            }
            while (!pending.isEmpty() && !Thread.currentThread().isInterrupted()) {
                long left = deadline - System.nanoTime();
                if (left <= 0) break;
                java.util.concurrent.Future<Probe> done =
                        completions.poll(left, TimeUnit.NANOSECONDS);
                if (done == null) break;
                DiscoveredEndpoint ep = pending.remove(done);
                try {
                    register(done.get(), discovered);
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException e) {
                    recordFailure(ep);
                }
            }
        } finally {
            // Whatever has not answered by the deadline counts as unreachable for a while.
            for (java.util.Map.Entry<java.util.concurrent.Future<Probe>, DiscoveredEndpoint> e
                    : pending.entrySet()) {
                e.getKey().cancel(true);
                recordFailure(e.getValue());
            }
            pool.shutdownNow();
        }
    }

    private Probe probe(DiscoveredEndpoint ep, long deadline) {
        String host = ep.host;
        if (ep.preferred && !isNumericAddress(host)) {
            // Built-in seeds are DNS names; peers are only ever registered by address.
            try {
                host = java.net.InetAddress.getByName(host).getHostAddress();
            } catch (java.io.IOException | RuntimeException unresolved) {
                return new Probe(ep, ep.host, Optional.empty());
            }
        }
        Optional<IdentityRecord> identity = authenticator instanceof DirectTcpPeerAuthenticator
                ? ((DirectTcpPeerAuthenticator) authenticator).authenticate(host, ep.port, deadline)
                : authenticator.authenticate(host, ep.port);
        return new Probe(ep, host, identity);
    }

    private void register(Probe probe, List<DiscoveredEndpoint> discovered) {
        DiscoveredEndpoint ep = probe.endpoint;
        String host = probe.host;
        int port = ep.port;
        if (probe.identity.isEmpty()) {
            recordFailure(ep);
            return;
        }
        IdentityRecord identity = probe.identity.get();
        if (ownEd25519Pub != null && Arrays.equals(identity.ed25519Pub(), ownEd25519Pub)) {
            LOG.debug("Skipping self discovery for " + host + ":" + port);
            quiet(ep, SELF_QUIET_MS);
            return;
        }
        clearBackoff(ep);
        byte[] peerPub = identity.ed25519Pub();
        boolean known = alreadyKnown(peerPub, host, port);
        directory.upsertVerified(peerPub, host, port, identity.rudpPort(),
                identity.capabilities(), identity.icebridgeVersion());
        if (!known) discovered.add(new DiscoveredEndpoint(host, port));

        // Feed known IceBridge relays (FORWARDER / BOTH) into the host cache
        // for the settings UI table and for faster post-restart bootstrapping.
        String role = identity.role();
        if ("FORWARDER".equals(role) || "BOTH".equals(role)) {
            try {
                com.frostwire.search.relay.icebridge.IceBridgeHostCache.getInstance()
                        .markSuccess(host, port, role);
            } catch (Throwable ignored) {
                // cache is best-effort
            }
        }
    }

    // ---- back-off ----

    private static final long ORDINARY_BACKOFF_BASE_MS = 30_000;
    private static final long ORDINARY_BACKOFF_MAX_MS = 10 * 60_000;
    private static final long PREFERRED_BACKOFF_BASE_MS = 5_000;
    private static final long PREFERRED_BACKOFF_MAX_MS = 60_000;
    private static final long SELF_QUIET_MS = 10 * 60_000;
    private static final int MAX_BACKOFF_ENTRIES = 2048;

    private static final class Backoff {
        final int failures;
        final long retryAtMs;

        Backoff(int failures, long retryAtMs) {
            this.failures = failures;
            this.retryAtMs = retryAtMs;
        }
    }

    private static final class Probe {
        final DiscoveredEndpoint endpoint;
        final String host;
        final Optional<IdentityRecord> identity;

        Probe(DiscoveredEndpoint endpoint, String host, Optional<IdentityRecord> identity) {
            this.endpoint = endpoint;
            this.host = host;
            this.identity = identity;
        }
    }

    private boolean isBackingOff(DiscoveredEndpoint ep, long nowMs) {
        Backoff backoff = this.backoff.get(ep.host + ":" + ep.port);
        return backoff != null && nowMs < backoff.retryAtMs;
    }

    private void recordFailure(DiscoveredEndpoint ep) {
        long base = ep.preferred ? PREFERRED_BACKOFF_BASE_MS : ORDINARY_BACKOFF_BASE_MS;
        long max = ep.preferred ? PREFERRED_BACKOFF_MAX_MS : ORDINARY_BACKOFF_MAX_MS;
        long now = clockMs.getAsLong();
        if (backoff.size() >= MAX_BACKOFF_ENTRIES) {
            backoff.values().removeIf(b -> b.retryAtMs <= now);
            if (backoff.size() >= MAX_BACKOFF_ENTRIES) backoff.clear();
        }
        backoff.merge(ep.host + ":" + ep.port, new Backoff(1, now + base), (old, fresh) -> {
            int failures = Math.min(old.failures + 1, 30);
            long delay = Math.min(max, base << Math.min(failures - 1, 20));
            return new Backoff(failures, now + delay);
        });
    }

    private void quiet(DiscoveredEndpoint ep, long ms) {
        backoff.put(ep.host + ":" + ep.port, new Backoff(1, clockMs.getAsLong() + ms));
    }

    private void clearBackoff(DiscoveredEndpoint ep) {
        backoff.remove(ep.host + ":" + ep.port);
    }

    private static boolean isNumericAddress(String host) {
        if (host == null || host.isEmpty()) return false;
        if (host.indexOf(':') >= 0) return true;
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (!octet.matches("[0-9]{1,3}")) return false;
        }
        return true;
    }

    /** Probes run in parallel; each one's identity handshake stays bounded by the pass deadline. */
    public void setProbeConcurrency(int concurrency) {
        this.probeConcurrency = Math.max(1, Math.min(concurrency, 32));
    }

    /** Overall time budget of one discovery pass. */
    public void setPassTimeoutMs(int timeoutMs) {
        this.passTimeoutMs = Math.max(100, timeoutMs);
    }

    /** Clock used for back-off expiry; tests inject a fake one. */
    public void setClock(LongSupplier clockMs) {
        this.clockMs = clockMs != null ? clockMs : System::currentTimeMillis;
    }

    private boolean alreadyKnown(byte[] pub, String host, int port) {
        PeerDirectory.PeerInfo existing = directory.get(pub).orElse(null);
        return existing != null
                && existing.hostname().equals(host)
                && existing.utpPort() == port;
    }

    /**
     * Fetch the BEP 46 identity record for a known pubkey.
     * Returns null on any failure.
     */
    public IdentityRecord fetchIdentityRecord(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32 || Thread.currentThread().isInterrupted()) {
            return null;
        }
        try {
            Entry entry = source.fetchIdentityEntry(peerPub);
            if (entry == null || Thread.currentThread().isInterrupted()) {
                return null;
            }
            IdentityRecord record = IdentityRecord.fromEntry(entry);
            return Arrays.equals(record.ed25519Pub(), peerPub) ? record : null;
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

    private boolean isSelfHairpin(String host, int port) {
        try {
            Supplier<String> supplier = externalIpSupplier;
            if (supplier == null || selfRelayPort <= 0
                    || host == null || host.isEmpty()) {
                return false;
            }
            String ext = supplier.get();
            return ext != null && !ext.isEmpty()
                    && port == selfRelayPort
                    && (ext.equals(host) || ext.equalsIgnoreCase(host));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLocalEndpoint(String host) {
        if (host == null || host.isEmpty()) return false;
        if ("127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host)) {
            return true;
        }
        try {
            // Discovery yields numeric IPs. Do not do synchronous DNS merely to filter a hint.
            java.net.InetAddress addr;
            if (host.indexOf(':') >= 0 && host.matches("[0-9a-fA-F:.]+")) {
                addr = java.net.InetAddress.getByName(host);
            } else {
                String[] octets = host.split("\\.", -1);
                if (octets.length != 4) return false;
                byte[] bytes = new byte[4];
                for (int i = 0; i < bytes.length; i++) {
                    if (!octets[i].matches("[0-9]{1,3}")) return false;
                    int value = Integer.parseInt(octets[i]);
                    if (value > 255) return false;
                    bytes[i] = (byte) value;
                }
                addr = java.net.InetAddress.getByAddress(bytes);
            }
            if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                    || addr.isMulticastAddress()) {
                return true;
            }
            // Exact match against this machine's interface addresses (catches the machine's own LAN/WAN IPs if announced)
            for (java.util.Enumeration<java.net.NetworkInterface> ifaces =
                     java.net.NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                java.net.NetworkInterface iface = ifaces.nextElement();
                for (java.net.InterfaceAddress ia : iface.getInterfaceAddresses()) {
                    java.net.InetAddress localAddr = ia.getAddress();
                    if (localAddr != null && localAddr.equals(addr)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // resolution or interface enumeration failed; fall through
        }
        return false;
    }
}
