/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.search.relay.DhtAdvertiser;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.IncomingRelayServer;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.frostwire.search.relay.icebridge.udp.RudpServer;
import com.frostwire.util.Logger;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the IceBridge relay servent.
 *
 * <p>IceBridge is an independent, protocol-agnostic relay network. It exposes
 * a local HTTP control interface and an rUDP mesh to other IceBridge nodes.
 * Standalone cloud forwarders can embed a DHT session ({@link IceBridgeDhtSession})
 * so they appear on BEP 5 relay/bootstrap topics without a FrostWire desktop
 * process. Application protocols (e.g. distributed search) ride as opaque payloads.
 */
public final class IceBridgeServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IceBridgeServer.class);
    private static final long JANITOR_INITIAL_DELAY_SEC = 10;
    private static final long JANITOR_INTERVAL_SEC = 30;
    /** DHT re-announce interval for standalone forwarders (seconds). */
    private static final long DHT_ANNOUNCE_INTERVAL_SEC = 60;

    private final IceBridgeConfig config;
    private final IceBridgeTokens authTokens;
    private IdentityKeys identity;
    private String runtimeAuthToken;
    private PeerRegistry registry;
    private IceBridgeMetrics metrics;
    private ControlServer controlServer;
    private RudpServer rudpServer;
    private RudpSessionManager rudpSessionManager;
    private InboundMessageQueue inboundQueue;
    private ScheduledExecutorService janitor;
    private IncomingRelayServer relayServer;
    private IceBridgeDhtSession dhtSession;
    private DhtAdvertiser dhtAdvertiser;

    public static void main(String[] args) {
        configureStandaloneConsoleLogging();
        loadDotEnv();
        IceBridgeConfig config = parseArgs(args);

        long parentPid = parseParentPid(args);
        if (parentPid > 0) {
            startParentWatchdog(parentPid);
        }

        // --generate-token support: prints the new token *only* (once) to stdout.
        // New tokens are appended to the tokens file and take effect immediately (no restart).
        if (containsGenerateToken(args)) {
            new IceBridgeTokens(config.authTokensFile()).generateAndAdd();
            System.exit(0);
        }

        File tokensFile = config.authTokensFile();
        IceBridgeTokens authTokens = new IceBridgeTokens(tokensFile);

        System.out.println("IceBridge — FrostWire relay servent");
        System.out.println("  software version           = " + IceBridgeConstants.SOFTWARE_VERSION
                + " (code " + IceBridgeConstants.SOFTWARE_VERSION_CODE + ")");
        System.out.println("  protocol version           = " + IceBridgeConstants.PROTOCOL_VERSION);
        System.out.println("  topology N (mesh fanout)   = " + IceBridgeTopology.get().meshBroadcastFanout()
                + "  [LimeWire NUM_CONNECTIONS]");
        System.out.println("  topology M (search peers)  = " + IceBridgeTopology.get().searchPeerFanout()
                + "  [LimeWire MAX_LEAVES]");
        System.out.println("  topology mesh hop TTL      = " + IceBridgeTopology.get().meshHopTtl());
        System.out.println("  topology search TTL        = " + IceBridgeTopology.get().searchTtl());
        System.out.println("  topology soft max          = " + IceBridgeTopology.get().softMax()
                + "  [LimeWire SOFT_MAX]");
        System.out.println("  topology leaf UP links     = " + IceBridgeTopology.get().leafUltrapeerConnections());
        System.out.println();
        System.out.println("Configuration (from .env / ICEBRIDGE_* env vars):");
        System.out.println("  ICEBRIDGE_HOST              = " + config.host());
        System.out.println("  ICEBRIDGE_RUDP_PORT         = " + config.rudpPort() + " (UDP)");
        System.out.println("  ICEBRIDGE_RELAY_PORT        = " + config.relayPort() + " (TCP, identity handshake)");
        System.out.println("  ICEBRIDGE_CONTROL_HTTP_PORT = " + config.controlHttpPort() + " (TCP)");
        System.out.println("  ICEBRIDGE_ROLE              = " + config.role());
        System.out.println("  ICEBRIDGE_IDENTITY_FILE     = " + (config.identityFile() != null ? config.identityFile() : "(default)"));
        System.out.println("  ICEBRIDGE_AUTH_TOKENS_FILE  = " + tokensFile.getAbsolutePath());
        System.out.println("  ICEBRIDGE_MAX_PEERS         = " + config.maxPeers());
        System.out.println("  ICEBRIDGE_MAX_SESSIONS      = " + config.maxSessions());
        System.out.println("  ICEBRIDGE_PEER_TTL_SEC      = " + config.peerTtlSec());
        System.out.println("  ICEBRIDGE_MAX_QPS_PER_KEY   = " + config.maxQpsPerKey());
        System.out.println("  ICEBRIDGE_BOOTSTRAP         = " + config.bootstrap());
        System.out.println("  ICEBRIDGE_DHT               = " + config.dhtEnabled());
        System.out.println();

        if (!checkPortAvailable(config.host(), config.rudpPort(), true)) {
            System.err.println("ERROR: UDP port " + config.rudpPort() + " is already in use.");
            System.err.println("       Another IceBridge instance or FrostWire may already be running.");
            System.err.println("       To use a different port: ICEBRIDGE_RUDP_PORT=<port> ./gradlew icebridge");
            System.exit(1);
        }
        if (config.relayPort() > 0 && !checkPortAvailable(config.host(), config.relayPort(), false)) {
            System.err.println("WARNING: TCP port " + config.relayPort() + " (identity handshake) is already in use.");
            System.err.println("         Another IceBridge instance or FrostWire may already be running.");
            System.err.println("         To use a different port: ICEBRIDGE_RELAY_PORT=<port> ./gradlew icebridge");
        }
        if (config.controlHttpPort() > 0 && !checkPortAvailable(config.host(), config.controlHttpPort(), false)) {
            System.err.println("ERROR: TCP port " + config.controlHttpPort() + " is already in use.");
            System.err.println("       Another IceBridge instance or FrostWire may already be running.");
            System.err.println("       To use a different port: ICEBRIDGE_CONTROL_HTTP_PORT=<port> ./gradlew icebridge");
            System.exit(1);
        }

        try (IceBridgeServer server = new IceBridgeServer(config, authTokens)) {
            boolean sharedConsumer = config.role() != IceBridgeConfig.Role.CLIENT
                    && config.relayPort() > 0 && searchAppEnabled();
            server.start(sharedConsumer);
            com.frostwire.search.relay.SearchRelayApp searchApp = null;
            try {
                // Embedder mode (relayPort=0): the parent FrostWire process owns Protocol #1
                // (IncomingSearchRequestHandler + real LocalIndex). SearchRelayApp uses an
                // EmptyLocalIndex and its own /poll consumer — if started here it races the
                // parent and answers every mesh search with rows=0.
                // Standalone forwarders (EC2, relayPort>0) still need SearchRelayApp for
                // dual-envelope forward.
                if (sharedConsumer) {
                    searchApp = com.frostwire.search.relay.SearchRelayApp.start(server);
                    System.out.println("Search relay app started (Protocol #1 dual-envelope forward, empty index)");
                    System.out.flush();
                } else if (config.relayPort() <= 0) {
                    System.out.println(
                            "Search relay app skipped (relayPort=0 embedder mode; parent owns Protocol #1)");
                    System.out.flush();
                }
                System.out.println();
                System.out.println("IceBridge is running. (Ctrl-C stops it only when attached to a terminal.)");
                System.out.println("  Health:  curl -sS http://127.0.0.1:" + config.controlHttpPort() + "/health");
                System.out.println("  Metrics: curl -sS -H \"X-IceBridge-Token: <token>\" http://127.0.0.1:"
                        + config.controlHttpPort() + "/metrics");
                System.out.println("  (TCP " + config.relayPort()
                        + " probes that are not FrostWire protocol are ignored at DEBUG — scanners/BT clients are normal.)");
                System.out.flush();
                Thread.sleep(Long.MAX_VALUE);
            } finally {
                if (searchApp != null) {
                    searchApp.close();
                }
            }
        } catch (Throwable t) {
            System.err.println();
            System.err.println("FATAL: IceBridge failed to start: " + t.getMessage());
            System.err.println();
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /** Search app layer (dual-envelope forward) is on unless ICEBRIDGE_SEARCH_APP=false. */
    private static boolean searchAppEnabled() {
        String v = System.getenv("ICEBRIDGE_SEARCH_APP");
        if (v == null || v.isEmpty()) {
            v = System.getProperty("ICEBRIDGE_SEARCH_APP");
        }
        return v == null || v.isEmpty()
                || !(v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("no"));
    }

    private static boolean checkPortAvailable(String host, int port, boolean udp) {
        try {
            if (udp) {
                java.net.DatagramSocket socket = new java.net.DatagramSocket(port, java.net.InetAddress.getByName(host));
                socket.setReuseAddress(true);
                socket.close();
            } else {
                java.net.ServerSocket socket = new java.net.ServerSocket(port, 0, java.net.InetAddress.getByName(host));
                socket.setReuseAddress(true);
                socket.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Load a {@code .env} file from the current directory if it exists.
     * Sets variables as system properties so {@link IceBridgeConfig#fromEnv()}
     * can read them via {@code System.getenv()} (via {@code -D} fallback).
     */
    private static void loadDotEnv() {
        // A systemd state directory is service-writable, never configuration.
        if ("false".equalsIgnoreCase(System.getenv("ICEBRIDGE_LOAD_DOT_ENV"))) {
            return;
        }
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (key.matches("ICEBRIDGE_[A-Z0-9_]+")
                        && System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (Throwable t) {
            LOG.warn("Failed to load .env file", t);
        }
    }

    /**
     * Ensure INFO (and above) mesh/protocol lines appear on stdout when
     * running {@code java -jar icebridge.jar} / icebridge-run-local.sh on a
     * cloud host — operators should see HELLO, RELAY, SEARCH, TELEMETRY/PING.
     */
    private static void configureStandaloneConsoleLogging() {
        try {
            java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
            root.setLevel(java.util.logging.Level.INFO);
            boolean hasConsole = false;
            for (java.util.logging.Handler h : root.getHandlers()) {
                h.setLevel(java.util.logging.Level.INFO);
                if (h instanceof java.util.logging.ConsoleHandler) {
                    hasConsole = true;
                }
            }
            if (!hasConsole) {
                java.util.logging.ConsoleHandler ch = new java.util.logging.ConsoleHandler();
                ch.setLevel(java.util.logging.Level.INFO);
                root.addHandler(ch);
            }
        } catch (Throwable ignored) {
            // Never fail start for logging setup.
        }
    }

    public IceBridgeServer(IceBridgeConfig config) {
        this(config, (IceBridgeTokens) null);
    }

    public IceBridgeServer(IceBridgeConfig config, IceBridgeTokens authTokens) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        this.config = config;
        this.authTokens = (authTokens != null) ? authTokens : new IceBridgeTokens(config.authTokensFile());
    }

    /**
     * Start an embedded server with no shared consumer. Clients must register their
     * identity consumer before delivery; role and listening ports do not imply ownership.
     */
    public synchronized void start() throws IOException, GeneralSecurityException, InterruptedException {
        start(false);
    }

    /**
     * Load or create identity, start listeners, and schedule registry cleanup.
     * If sharedConsumerEnabled is true, the caller owns draining raw /poll for
     * the server lifetime (standalone SearchRelayApp or an explicit raw consumer).
     */
    public synchronized void start(boolean sharedConsumerEnabled)
            throws IOException, GeneralSecurityException, InterruptedException {
        if (identity != null) {
            throw new IllegalStateException("server already started");
        }
        ensureRuntimeAuthToken();
        this.identity = loadIdentity(config.identityFile());
        this.metrics = new IceBridgeMetrics();
        this.registry = new PeerRegistry(config);
        this.inboundQueue = new InboundMessageQueue();
        this.inboundQueue.setSharedConsumerEnabled(sharedConsumerEnabled);
        this.rudpSessionManager = new RudpSessionManager(identity, registry, metrics, inboundQueue);
        this.rudpSessionManager.setMaxSessions(config.maxSessions());
        this.controlServer = new ControlServer(registry, metrics, config, rudpSessionManager, inboundQueue, this.authTokens);
        this.rudpServer = new RudpServer(config, rudpSessionManager);

        try {
            controlServer.start();
            rudpServer.start();
            startRelayServer();
            startJanitor();
            startDhtAnnouncer();
        } catch (InterruptedException | RuntimeException | Error e) {
            close();
            throw e;
        }

        LOG.info("IceBridge started: identity=" + Hex.encode(identity.ed25519PubRaw())
                + " role=" + config.role()
                + " rudpPort=" + rudpServer.port()
                + " httpPort=" + controlServer.port()
                + " dht=" + (dhtAdvertiser != null));
    }

    private IdentityKeys loadIdentity(File file) throws IOException, GeneralSecurityException {
        if (file == null) {
            File defaultDir = new File(System.getProperty("user.home"), ".frostwire");
            file = new File(defaultDir, "icebridge-identity.dat");
        }
        // CLI-visible progress: first-run PoW can take seconds; never leave operators guessing.
        boolean exists = file.exists() && file.length() > 0;
        if (exists) {
            System.out.println("Loading identity from " + file.getAbsolutePath() + " …");
        } else {
            System.out.println("No identity file yet — mining proof-of-work identity"
                    + " (" + com.frostwire.search.relay.KarmaConstants.IDENTITY_DIFFICULTY
                    + " leading zero bits). This usually takes a few seconds (native Ed25519)."
                    + " Please wait…");
            System.out.flush();
        }
        long t0 = System.currentTimeMillis();
        IdentityKeys keys = IdentityKeys.loadOrCreate(file);
        long ms = System.currentTimeMillis() - t0;
        if (!exists) {
            System.out.println("Identity ready in " + ms + " ms → " + file.getAbsolutePath());
        } else {
            System.out.println("Identity loaded in " + ms + " ms.");
        }
        System.out.flush();
        return keys;
    }

    private void startJanitor() {
        janitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-janitor");
            t.setDaemon(true);
            return t;
        });
        janitor.scheduleWithFixedDelay(this::runJanitor,
                JANITOR_INITIAL_DELAY_SEC, JANITOR_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void startRelayServer() {
        int relayPort = config.relayPort();
        // relayPort=0: embedder owns IncomingRelayServer (Android starts a full
        // RelayRole server on RELAY_LISTEN_PORT; dual-bind causes EADDRINUSE).
        if (relayPort <= 0) {
            LOG.info("IceBridge identity handshake TCP server disabled (relayPort=0; external owner)");
            return;
        }
        try {
            IdentityRecord record = IdentityRecord.createSigned(
                    identity.nodeId(), identity.ed25519(),
                    identity.x25519PubRaw(), relayPort,
                    rudpServer.port(), config.role().name());
            relayServer = new IncomingRelayServer(record, identity.ed25519().getPrivate(), relayPort, config.host());
            relayServer.start();
            LOG.info("IceBridge identity handshake server listening on " + config.host() + ":" + relayPort + " (TCP)");
        } catch (Throwable t) {
            LOG.warn("Failed to start identity handshake server on port " + relayPort
                    + "; peers will not be able to authenticate this relay via TCP", t);
        }
    }

    /**
     * Start embedded DHT announce when {@link IceBridgeConfig#dhtEnabled()}.
     * Fail-closed: mesh stays up if native DHT fails to start.
     */
    private void startDhtAnnouncer() {
        if (!config.dhtEnabled()) {
            LOG.info("IceBridge DHT announce disabled (ICEBRIDGE_DHT=false or builder default)");
            return;
        }
        try {
            dhtSession = IceBridgeDhtSession.start(config.host());
            // BEP 5 advertise TCP identity port; rUDP port is in IdentityRecord (BEP 46).
            IdentityRecordPublisher publisher = new IdentityRecordPublisher(
                    identity,
                    config.relayPort(),
                    config.rudpPort() > 0 ? config.rudpPort() : rudpServer.port(),
                    config.role().name());
            // Pure FORWARDER: relay (+ optional bootstrap) only — not a search peer.
            // BOTH: also peer topic. CLIENT with DHT is unusual; peer topic only.
            boolean peerTopic = config.role() != IceBridgeConfig.Role.FORWARDER;
            boolean bootstrapTopic = config.bootstrap();
            dhtAdvertiser = new DhtAdvertiser(
                    publisher,
                    null,
                    DHT_ANNOUNCE_INTERVAL_SEC,
                    () -> dhtSession != null ? dhtSession.session() : null,
                    peerTopic,
                    bootstrapTopic);
            dhtAdvertiser.start();
            LOG.info("IceBridge DHT announcer started: peerTopic=" + peerTopic
                    + " bootstrapTopic=" + bootstrapTopic
                    + " announcePort=" + config.relayPort());
        } catch (Throwable t) {
            LOG.warn("IceBridge DHT announcer failed to start; relay mesh continues without DHT visibility", t);
            stopDhtAnnouncer();
        }
    }

    private void stopDhtAnnouncer() {
        if (dhtAdvertiser != null) {
            try {
                dhtAdvertiser.stop();
            } catch (Throwable ignored) {
            }
            dhtAdvertiser = null;
        }
        if (dhtSession != null) {
            try {
                dhtSession.close();
            } catch (Throwable ignored) {
            }
            dhtSession = null;
        }
    }

    /** For tests / diagnostics. */
    public boolean isDhtAnnouncerRunning() {
        return dhtAdvertiser != null && dhtAdvertiser.isRunning();
    }

    private void runJanitor() {
        try {
            long ttlMs = Math.multiplyExact(config.peerTtlSec(), 1000L);
            int removed = registry.evictStale(ttlMs);
            if (removed > 0) {
                LOG.info("IceBridge janitor evicted " + removed + " stale peers; registry size=" + registry.size());
            }
        } catch (Throwable t) {
            LOG.warn("IceBridge janitor failed", t);
        }
    }

    public IceBridgeConfig config() {
        return config;
    }

    public IdentityKeys identity() {
        return identity;
    }

    public PeerRegistry registry() {
        return registry;
    }

    public IceBridgeMetrics metrics() {
        return metrics;
    }

    public RudpSessionManager rudpSessionManager() {
        return rudpSessionManager;
    }

    /** Enables delivery only when an embedder explicitly owns the shared poll consumer. */
    public synchronized boolean setSharedConsumerEnabled(boolean enabled) {
        return inboundQueue != null && inboundQueue.setSharedConsumerEnabled(enabled);
    }

    public int controlPort() {
        return controlServer == null ? 0 : controlServer.port();
    }

    /**
     * Returns a bearer token for co-located clients (local child launcher, in-process
     * Android stack, tests). Provisioning happens in {@link #start()} when the tokens
     * file is empty; child launchers supply a restricted per-launch tokens file.
     */
    public String authToken() {
        if (runtimeAuthToken != null) {
            return runtimeAuthToken;
        }
        return authTokens != null ? authTokens.clientToken() : null;
    }

    private void ensureRuntimeAuthToken() {
        if (authTokens == null || !authTokens.isEmpty()) {
            return;
        }
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        runtimeAuthToken = com.frostwire.util.Hex.encode(b);
        authTokens.addRuntimeToken(runtimeAuthToken);
        LOG.info("Provisioned runtime auth token (no entries in tokens file)");
    }

    public int rudpPort() {
        return rudpServer == null ? 0 : rudpServer.port();
    }

    @Override
    public synchronized void close() {
        LOG.info("Shutting down IceBridge");
        stopDhtAnnouncer();
        if (janitor != null) {
            janitor.shutdownNow();
        }
        if (relayServer != null) {
            try {
                relayServer.stop();
            } catch (Throwable ignored) {
            }
        }
        if (rudpServer != null) {
            try {
                rudpServer.close();
            } catch (Throwable ignored) {
            }
        }
        if (controlServer != null) {
            try {
                controlServer.close();
            } catch (Throwable ignored) {
            }
        }
        LOG.info("IceBridge shutdown complete");
    }

    /**
     * Parse command-line arguments into a config.
     */
    public static IceBridgeConfig parseArgs(String[] args) {
        Map<String, String> overrides = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--rudp-port":
                    overrides.put("ICEBRIDGE_RUDP_PORT", next(args, ++i, arg));
                    break;
                case "--relay-port":
                    overrides.put("ICEBRIDGE_RELAY_PORT", next(args, ++i, arg));
                    break;
                case "--control-http-port":
                    int controlPort = parseInt(next(args, ++i, "--control-http-port"));
                    if (controlPort <= 0) {
                        throw new IllegalArgumentException(
                                "--control-http-port must be > 0 (HTTP control is required)");
                    }
                    overrides.put("ICEBRIDGE_CONTROL_HTTP_PORT", Integer.toString(controlPort));
                    break;
                case "--role":
                    overrides.put("ICEBRIDGE_ROLE", next(args, ++i, arg));
                    break;
                case "--identity-file":
                    overrides.put("ICEBRIDGE_IDENTITY_FILE", next(args, ++i, arg));
                    break;
                case "--max-peers":
                    overrides.put("ICEBRIDGE_MAX_PEERS", next(args, ++i, arg));
                    break;
                case "--max-sessions":
                    overrides.put("ICEBRIDGE_MAX_SESSIONS", next(args, ++i, arg));
                    break;
                case "--peer-ttl-sec":
                    overrides.put("ICEBRIDGE_PEER_TTL_SEC", next(args, ++i, arg));
                    break;
                case "--max-qps-per-key":
                    overrides.put("ICEBRIDGE_MAX_QPS_PER_KEY", next(args, ++i, arg));
                    break;
                case "--bootstrap":
                    overrides.put("ICEBRIDGE_BOOTSTRAP", "true");
                    break;
                case "--no-bootstrap":
                    overrides.put("ICEBRIDGE_BOOTSTRAP", "false");
                    break;
                case "--dht":
                    overrides.put("ICEBRIDGE_DHT", "true");
                    break;
                case "--no-dht":
                    overrides.put("ICEBRIDGE_DHT", "false");
                    break;
                case "--host":
                    overrides.put("ICEBRIDGE_HOST", next(args, ++i, arg));
                    break;
                case "--auth-token":
                    throw new IllegalArgumentException("Use --auth-tokens-file; bearer tokens must not appear in process arguments");
                case "--parent-pid":
                    // Parsed separately by parseParentPid(); skip value.
                    Long.parseLong(next(args, ++i, arg));
                    break;
                case "--auth-tokens-file":
                    overrides.put("ICEBRIDGE_AUTH_TOKENS_FILE", next(args, ++i, arg));
                    break;
                case "--generate-token":
                    // Handled early in main.
                    break;
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option; use --help");
            }
        }
        return IceBridgeConfig.fromEnv(overrides);
    }

    private static String next(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[i];
    }

    private static int parseInt(String s) {
        return Integer.parseInt(s);
    }

    private static void printHelp() {
        System.out.println("IceBridge — FrostWire relay servent");
        System.out.println("Options:");
        System.out.println("  CLI overrides exported ICEBRIDGE_* env, then system properties/.env, then role defaults.");
        System.out.println("  --rudp-port PORT           rUDP listen port (0 = disable, auto for local)");
        System.out.println("  --relay-port PORT          TCP identity/relay handshake port (default 6888)");
        System.out.println("  --control-http-port PORT   HTTP control port on 127.0.0.1 (required, must be > 0)");
        System.out.println("  --role ROLE                FORWARDER, CLIENT, or BOTH");
        System.out.println("  --identity-file PATH       Ed25519 identity file");
        System.out.println("  --max-peers N              Maximum tracked peers");
        System.out.println("  --max-sessions N           Maximum concurrent rUDP sessions (default 1024)");
        System.out.println("  --peer-ttl-sec N           Peer eviction TTL");
        System.out.println("  --max-qps-per-key N        Registration rate limit per public key");
        System.out.println("  --bootstrap / --no-bootstrap  Enable/disable bootstrap topic (independent of DHT)");
        System.out.println("  --dht                      Embed DHT SessionManager and announce on relay topics");
        System.out.println("  --no-dht                   Disable embedded DHT (default enabled for FORWARDER/BOTH)");
        System.out.println("  --host HOST                Bind host");
        System.out.println("  --auth-tokens-file PATH    File with one bearer token per line (default icebridge-tokens.txt)");
        System.out.println("  --generate-token           Generate + print one new token (only the token to stdout), store it, exit");
        System.out.println("  --parent-pid PID           Exit when the parent process dies (child mode; prevents orphans)");
    }

    /**
     * Parent watchdog: when spawned as a child process with {@code
     * --parent-pid}, exit as soon as the parent FrostWire dies. Prevents
     * orphaned children from surviving a crashed/killed parent and stealing
     * the rUDP port from the next session (#917/#937/#944).
     */
    private static long parseParentPid(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--parent-pid".equals(args[i]) && i + 1 < args.length) {
                try {
                    return Long.parseLong(args[i + 1]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static void startParentWatchdog(long parentPid) {
        Thread watchdog = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
                if (!isProcessAlive(parentPid)) {
                    System.err.println("Parent process " + parentPid
                            + " is gone — exiting to avoid becoming an orphan relay.");
                    System.exit(0);
                }
            }
        }, "icebridge-parent-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        System.out.println("Parent watchdog active: exiting if parent pid " + parentPid + " dies.");
    }

    /**
     * Parent liveness via {@code java.lang.ProcessHandle}, accessed
     * reflectively so this common source compiles on Android (no
     * ProcessHandle in the Android bootclasspath). The watchdog itself only
     * runs in spawned child processes (desktop/EC2 JVMs); on platforms
     * without ProcessHandle we conservatively report the parent alive.
     */
    private static boolean isProcessAlive(long pid) {
        try {
            Class<?> processHandle = Class.forName("java.lang.ProcessHandle");
            Object optional = processHandle.getMethod("of", long.class).invoke(null, pid);
            Object handle = ((java.util.Optional<?>) optional).orElse(null);
            return handle != null && (boolean) handle.getClass().getMethod("isAlive").invoke(handle);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean containsGenerateToken(String[] args) {
        for (String a : args) if ("--generate-token".equals(a)) return true;
        return false;
    }

    /** Small Hex helper so the server can log its own identity. */
    private static final class Hex {
        private static final char[] DIGITS = "0123456789abcdef".toCharArray();

        static String encode(byte[] bytes) {
            if (bytes == null) {
                return "";
            }
            char[] out = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xff;
                out[i * 2] = DIGITS[v >>> 4];
                out[i * 2 + 1] = DIGITS[v & 0x0f];
            }
            return new String(out);
        }
    }
}
