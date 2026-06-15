/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.control.ControlServer;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpServer;
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
 * <p>IceBridge is a purpose-agnostic relay layer. It exposes a local HTTP/
 * stdio control interface to FrostWire and an rUDP mesh interface to other
 * IceBridge servents. This class initializes the identity, control server,
 * rUDP listener, peer registry, and housekeeping threads.
 */
public final class IceBridgeServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IceBridgeServer.class);
    private static final long JANITOR_INITIAL_DELAY_SEC = 10;
    private static final long JANITOR_INTERVAL_SEC = 30;

    private final IceBridgeConfig config;
    private IdentityKeys identity;
    private PeerRegistry registry;
    private IceBridgeMetrics metrics;
    private ControlServer controlServer;
    private RudpServer rudpServer;
    private ScheduledExecutorService janitor;

    public static void main(String[] args) throws Exception {
        IceBridgeConfig config = parseArgs(args);
        try (IceBridgeServer server = new IceBridgeServer(config)) {
            server.start();
            LOG.info("IceBridge running, press Ctrl-C to stop");
            Thread.sleep(Long.MAX_VALUE);
        }
    }

    public IceBridgeServer(IceBridgeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        this.config = config;
    }

    /**
     * Load or create identity, start listeners, and schedule registry cleanup.
     */
    public synchronized void start() throws IOException, GeneralSecurityException, InterruptedException {
        if (identity != null) {
            throw new IllegalStateException("server already started");
        }
        this.identity = loadIdentity(config.identityFile());
        this.metrics = new IceBridgeMetrics();
        this.registry = new PeerRegistry(config);
        this.controlServer = new ControlServer(registry, metrics, config);
        this.rudpServer = new RudpServer(config, metrics);

        controlServer.start();
        rudpServer.start();
        startJanitor();

        LOG.info("IceBridge started: identity=" + Hex.encode(identity.ed25519PubRaw())
                + " role=" + config.role()
                + " rudpPort=" + rudpServer.port()
                + " httpPort=" + controlServer.port());
    }

    private IdentityKeys loadIdentity(File file) throws IOException, GeneralSecurityException {
        if (file == null) {
            File defaultDir = new File(System.getProperty("user.home"), ".frostwire");
            file = new File(defaultDir, "icebridge-identity.dat");
        }
        return IdentityKeys.loadOrCreate(file);
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

    private void runJanitor() {
        try {
            long ttlMs = config.peerTtlSec() * 1000;
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

    public int controlPort() {
        return controlServer == null ? 0 : controlServer.port();
    }

    public int rudpPort() {
        return rudpServer == null ? 0 : rudpServer.port();
    }

    @Override
    public synchronized void close() {
        LOG.info("Shutting down IceBridge");
        if (janitor != null) {
            janitor.shutdownNow();
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
                case "--control-http-port":
                    b.controlHttpPort(parseInt(next(args, ++i, "--control-http-port")));
                    break;
                case "--control-stdio":
                    b.controlStdio(true);
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
                    break;
                case "--host":
                    b.host(next(args, ++i, "--host"));
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
        System.out.println("  --control-http-port PORT   HTTP control port (0 = disable)");
        System.out.println("  --control-stdio            Enable stdio control channel");
        System.out.println("  --role ROLE                FORWARDER, CLIENT, or BOTH");
        System.out.println("  --identity-file PATH       Ed25519 identity file");
        System.out.println("  --max-peers N              Maximum tracked peers");
        System.out.println("  --peer-ttl-sec N           Peer eviction TTL");
        System.out.println("  --max-qps-per-key N        Registration rate limit per public key");
        System.out.println("  --bootstrap                Advertise bootstrap DHT topic");
        System.out.println("  --host HOST                Bind host");
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