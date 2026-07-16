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
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.frostwire.search.relay.icebridge.udp.RudpServer;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
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

        // --generate-token support: prints the new token *only* (once) to stdout.
        // New tokens are appended to the tokens file and take effect immediately (no restart).
        if (containsGenerateToken(args)) {
            File tf = resolveAuthTokensFile(args);
            new IceBridgeTokens(tf).generateAndAdd();
            System.exit(0);
        }

        IceBridgeConfig config;
        String cliAuthToken = null;
        if (args.length == 0) {
            config = IceBridgeConfig.fromEnv();
        } else {
            config = parseArgs(args);
            cliAuthToken = parseAuthToken(args);
        }

        File tokensFile = resolveAuthTokensFile(args, config);
        IceBridgeTokens authTokens = new IceBridgeTokens(tokensFile);

        if (cliAuthToken != null && !cliAuthToken.isEmpty()) {
            authTokens.addRuntimeToken(cliAuthToken);
            LOG.info("CLI --auth-token added for this run (prefer the tokens file for persistence and multiple tokens)");
        }

        System.out.println("IceBridge — FrostWire relay servent");
        System.out.println("  software version           = " + IceBridgeConstants.SOFTWARE_VERSION
                + " (code " + IceBridgeConstants.SOFTWARE_VERSION_CODE + ")");
        System.out.println("  protocol version           = " + IceBridgeConstants.PROTOCOL_VERSION);
        System.out.println("  topology N (mesh fanout)   = " + IceBridgeTopology.get().meshBroadcastFanout());
        System.out.println("  topology M (search peers)  = " + IceBridgeTopology.get().searchPeerFanout());
        System.out.println("  topology mesh hop TTL      = " + IceBridgeTopology.get().meshHopTtl());
        System.out.println("  topology search TTL        = " + IceBridgeTopology.get().searchTtl());
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
            server.start();
            System.out.println();
            System.out.println("IceBridge is running. Press Ctrl-C to stop.");
            System.out.println("  Health:  curl -sS http://127.0.0.1:" + config.controlHttpPort() + "/health");
            System.out.println("  Metrics: curl -sS -H \"X-IceBridge-Token: <token>\" http://127.0.0.1:"
                    + config.controlHttpPort() + "/metrics");
            System.out.println("  (TCP " + config.relayPort()
                    + " probes that are not FrostWire protocol are ignored at DEBUG — scanners/BT clients are normal.)");
            System.out.flush();
            Thread.sleep(Long.MAX_VALUE);
        } catch (Throwable t) {
            System.err.println();
            System.err.println("FATAL: IceBridge failed to start: " + t.getMessage());
            System.err.println();
            t.printStackTrace(System.err);
            System.exit(1);
        }
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
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (Throwable t) {
            LOG.warn("Failed to load .env file", t);
        }
    }

    private static String parseAuthToken(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--auth-token".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
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
     * Load or create identity, start listeners, and schedule registry cleanup.
     */
    public synchronized void start() throws IOException, GeneralSecurityException, InterruptedException {
        if (identity != null) {
            throw new IllegalStateException("server already started");
        }
        ensureRuntimeAuthToken();
        this.identity = loadIdentity(config.identityFile());
        this.metrics = new IceBridgeMetrics();
        this.registry = new PeerRegistry(config);
        this.inboundQueue = new InboundMessageQueue();
        this.rudpSessionManager = new RudpSessionManager(identity, registry, metrics, inboundQueue);
        this.controlServer = new ControlServer(registry, metrics, config, rudpSessionManager, inboundQueue, this.authTokens);
        this.rudpServer = new RudpServer(config, rudpSessionManager);

        controlServer.start();
        rudpServer.start();
        startRelayServer();
        startJanitor();
        startDhtAnnouncer();

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
                    config.rudpPort(), config.role().name());
            relayServer = new IncomingRelayServer(record, relayPort, config.host());
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

    public int controlPort() {
        return controlServer == null ? 0 : controlServer.port();
    }

    /**
     * Returns a bearer token for co-located clients (local child launcher, in-process
     * Android stack, tests). Provisioning happens in {@link #start()} when the tokens
     * file is empty; CLI {@code --auth-token} values are added before start.
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
        IceBridgeConfig.Builder b = IceBridgeConfig.newBuilder();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--rudp-port":
                    b.rudpPort(parseInt(next(args, ++i, "--rudp-port")));
                    break;
                case "--relay-port":
                    b.relayPort(parseInt(next(args, ++i, "--relay-port")));
                    break;
                case "--control-http-port":
                    int controlPort = parseInt(next(args, ++i, "--control-http-port"));
                    if (controlPort <= 0) {
                        throw new IllegalArgumentException(
                                "--control-http-port must be > 0 (HTTP control is required)");
                    }
                    b.controlHttpPort(controlPort);
                    break;
                case "--role":
                    b.role(IceBridgeConfig.Role.valueOf(next(args, ++i, "--role").toUpperCase()));
                    break;
                case "--identity-file":
                    b.identityFile(new File(next(args, ++i, "--identity-file")));
                    break;
                case "--max-peers":
                    b.maxPeers(parseInt(next(args, ++i, "--max-peers")));
                    break;
                case "--peer-ttl-sec":
                    b.peerTtlSec(parseLong(next(args, ++i, "--peer-ttl-sec")));
                    break;
                case "--max-qps-per-key":
                    b.maxQpsPerKey(parseDouble(next(args, ++i, "--max-qps-per-key")));
                    break;
                case "--bootstrap":
                    b.bootstrap(true);
                    b.dhtEnabled(true);
                    break;
                case "--dht":
                    b.dhtEnabled(true);
                    break;
                case "--no-dht":
                    b.dhtEnabled(false);
                    break;
                case "--host":
                    b.host(next(args, ++i, "--host"));
                    break;
                case "--auth-token":
                    // Parsed separately by parseAuthToken(); skip value.
                    i++;
                    break;
                case "--auth-tokens-file":
                    // Handled early in main; skip value here.
                    i++;
                    break;
                case "--generate-token":
                    // Handled early in main.
                    break;
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return b.build();
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

    private static long parseLong(String s) {
        return Long.parseLong(s);
    }

    private static double parseDouble(String s) {
        return Double.parseDouble(s);
    }

    private static void printHelp() {
        System.out.println("IceBridge — FrostWire relay servent");
        System.out.println("Options:");
        System.out.println("  --rudp-port PORT           rUDP listen port (0 = disable, auto for local)");
        System.out.println("  --relay-port PORT          TCP identity/relay handshake port (default 6888)");
        System.out.println("  --control-http-port PORT   HTTP control port on 127.0.0.1 (required, must be > 0)");
        System.out.println("  --role ROLE                FORWARDER, CLIENT, or BOTH");
        System.out.println("  --identity-file PATH       Ed25519 identity file");
        System.out.println("  --max-peers N              Maximum tracked peers");
        System.out.println("  --peer-ttl-sec N           Peer eviction TTL");
        System.out.println("  --max-qps-per-key N        Registration rate limit per public key");
        System.out.println("  --bootstrap                Advertise bootstrap DHT topic (enables embedded DHT)");
        System.out.println("  --dht                      Embed DHT SessionManager and announce on relay topics");
        System.out.println("  --no-dht                   Disable embedded DHT (default when using CLI without --dht)");
        System.out.println("  --host HOST                Bind host");
        System.out.println("  --auth-tokens-file PATH    File with one bearer token per line (default icebridge-tokens.txt)");
        System.out.println("  --generate-token           Generate + print one new token (only the token to stdout), store it, exit");
    }

    private static boolean containsGenerateToken(String[] args) {
        for (String a : args) if ("--generate-token".equals(a)) return true;
        return false;
    }

    private static File resolveAuthTokensFile(String[] args) {
        return resolveAuthTokensFile(args, null);
    }

    private static File resolveAuthTokensFile(String[] args, IceBridgeConfig cfg) {
        // CLI takes precedence
        for (int i = 0; i < args.length; i++) {
            if ("--auth-tokens-file".equals(args[i]) && i + 1 < args.length) {
                return new File(args[i + 1]);
            }
        }
        // Then env / property (populated by loadDotEnv and gradle task)
        String fromEnv = System.getenv("ICEBRIDGE_AUTH_TOKENS_FILE");
        if (fromEnv == null || fromEnv.isEmpty()) fromEnv = System.getProperty("ICEBRIDGE_AUTH_TOKENS_FILE");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return new File(fromEnv);
        }
        if (cfg != null && cfg.authTokensFile() != null) {
            return cfg.authTokensFile();
        }
        return new File("icebridge-tokens.txt");
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