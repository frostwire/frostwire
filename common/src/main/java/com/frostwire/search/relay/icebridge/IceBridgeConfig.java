/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import java.io.File;
import java.util.Objects;

/**
 * Immutable configuration for an IceBridge servent.
 *
 * <p>IceBridge can run as a local daemon controlled by FrostWire, as a cloud
 * forwarder that helps NAT-restricted peers, or as a development embedded
 * instance. All network ports, role flags, and resource limits are captured
 * here.
 */
public final class IceBridgeConfig {

    /** Roles a servent can advertise. */
    public enum Role {
        /** Can receive incoming rUDP associations and forward traffic. */
        FORWARDER,
        /** Only initiates outgoing associations; routes through forwarders. */
        CLIENT,
        /** Both roles. */
        BOTH
    }

    private final String host;
    private final int rudpPort;
    private final int relayPort;
    private final int controlHttpPort;
    private final Role role;
    private final File identityFile;
    private final int maxPeers;
    private final long peerTtlSec;
    private final double maxQpsPerKey;
    private final boolean bootstrap;
    private final boolean dhtEnabled;
    private final File authTokensFile;

    public IceBridgeConfig(String host,
                           int rudpPort,
                           int controlHttpPort,
                           Role role,
                           File identityFile,
                           int maxPeers,
                           long peerTtlSec,
                           double maxQpsPerKey,
                           boolean bootstrap) {
        this(host, rudpPort, com.frostwire.search.relay.RelayConstants.RELAY_LISTEN_PORT,
             controlHttpPort, role, identityFile,
             maxPeers, peerTtlSec, maxQpsPerKey, bootstrap, false, null);
    }

    public IceBridgeConfig(String host,
                           int rudpPort,
                           int relayPort,
                           int controlHttpPort,
                           Role role,
                           File identityFile,
                           int maxPeers,
                           long peerTtlSec,
                           double maxQpsPerKey,
                           boolean bootstrap,
                           File authTokensFile) {
        this(host, rudpPort, relayPort, controlHttpPort, role, identityFile,
                maxPeers, peerTtlSec, maxQpsPerKey, bootstrap, false, authTokensFile);
    }

    public IceBridgeConfig(String host,
                           int rudpPort,
                           int relayPort,
                           int controlHttpPort,
                           Role role,
                           File identityFile,
                           int maxPeers,
                           long peerTtlSec,
                           double maxQpsPerKey,
                           boolean bootstrap,
                           boolean dhtEnabled,
                           File authTokensFile) {
        this.host = Objects.requireNonNullElse(host, "0.0.0.0");
        this.rudpPort = requirePositiveOrZero(rudpPort, "rudpPort");
        this.relayPort = requirePositiveOrZero(relayPort, "relayPort");
        this.controlHttpPort = requirePositive(controlHttpPort, "controlHttpPort");
        this.role = Objects.requireNonNullElse(role, Role.CLIENT);
        this.identityFile = identityFile;
        this.maxPeers = requirePositive(maxPeers, "maxPeers");
        this.peerTtlSec = requirePositive(peerTtlSec, "peerTtlSec");
        this.maxQpsPerKey = requirePositive(maxQpsPerKey, "maxQpsPerKey");
        this.bootstrap = bootstrap;
        this.dhtEnabled = dhtEnabled;
        this.authTokensFile = authTokensFile != null ? authTokensFile : new File("icebridge-tokens.txt");
    }

    public String host() {
        return host;
    }

    public int rudpPort() {
        return rudpPort;
    }

    public int relayPort() {
        return relayPort;
    }

    public int controlHttpPort() {
        return controlHttpPort;
    }

    public Role role() {
        return role;
    }

    public File identityFile() {
        return identityFile;
    }

    public int maxPeers() {
        return maxPeers;
    }

    public long peerTtlSec() {
        return peerTtlSec;
    }

    public double maxQpsPerKey() {
        return maxQpsPerKey;
    }

    public boolean bootstrap() {
        return bootstrap;
    }

    /**
     * When true, standalone IceBridge starts an embedded DHT session and
     * announces under relay/bootstrap topics. Default false for in-process
     * / test builders (desktop/Android already advertise via BTEngine).
     * Standalone {@link #fromEnv()} defaults this on for FORWARDER/BOTH.
     */
    public boolean dhtEnabled() {
        return dhtEnabled;
    }

    public File authTokensFile() {
        return authTokensFile;
    }

    public boolean canAcceptIncoming() {
        return role == Role.FORWARDER || role == Role.BOTH;
    }

    /** Default config for a local daemon controlled by FrostWire (binds publicly by default). */
    public static IceBridgeConfig localDefaults() {
        return newBuilder()
                .host("0.0.0.0")
                .rudpPort(0)
                .controlHttpPort(8797)
                .role(Role.BOTH)
                .maxPeers(1000)
                .peerTtlSec(120)
                .maxQpsPerKey(5.0)
                .build();
    }

    /** Default config for a headless cloud forwarder. */
    public static IceBridgeConfig cloudDefaults() {
        return newBuilder()
                .rudpPort(6889)
                .controlHttpPort(8081)
                .role(Role.FORWARDER)
                .maxPeers(10000)
                .peerTtlSec(120)
                .maxQpsPerKey(10.0)
                .bootstrap(true)
                .dhtEnabled(true)
                .build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "0.0.0.0";
        private int rudpPort;
        private int relayPort = com.frostwire.search.relay.RelayConstants.RELAY_LISTEN_PORT;
        private int controlHttpPort;
        private Role role = Role.CLIENT;
        private File identityFile;
        private int maxPeers = 1000;
        private long peerTtlSec = 120;
        private double maxQpsPerKey = 5.0;
        private boolean bootstrap;
        private boolean dhtEnabled;
        private File authTokensFile;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder rudpPort(int rudpPort) {
            this.rudpPort = rudpPort;
            return this;
        }

        public Builder relayPort(int relayPort) {
            this.relayPort = relayPort;
            return this;
        }

        public Builder controlHttpPort(int controlHttpPort) {
            this.controlHttpPort = controlHttpPort;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder identityFile(File identityFile) {
            this.identityFile = identityFile;
            return this;
        }

        public Builder maxPeers(int maxPeers) {
            this.maxPeers = maxPeers;
            return this;
        }

        public Builder peerTtlSec(long peerTtlSec) {
            this.peerTtlSec = peerTtlSec;
            return this;
        }

        public Builder maxQpsPerKey(double maxQpsPerKey) {
            this.maxQpsPerKey = maxQpsPerKey;
            return this;
        }

        public Builder bootstrap(boolean bootstrap) {
            this.bootstrap = bootstrap;
            return this;
        }

        public Builder dhtEnabled(boolean dhtEnabled) {
            this.dhtEnabled = dhtEnabled;
            return this;
        }

        public Builder authTokensFile(File authTokensFile) {
            this.authTokensFile = authTokensFile;
            return this;
        }

        public IceBridgeConfig build() {
            return new IceBridgeConfig(host, rudpPort, relayPort, controlHttpPort, role,
                    identityFile, maxPeers, peerTtlSec, maxQpsPerKey, bootstrap, dhtEnabled,
                    authTokensFile);
        }
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static int requirePositiveOrZero(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static double requirePositive(double value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    /**
     * Build a config from environment variables prefixed with {@code ICEBRIDGE_}.
     *
     * <p>Supported variables:
     * <ul>
     *   <li>{@code ICEBRIDGE_HOST} — bind address (default: 0.0.0.0)</li>
     *   <li>{@code ICEBRIDGE_RUDP_PORT} — rUDP listen port (default: 6889)</li>
     *   <li>{@code ICEBRIDGE_CONTROL_HTTP_PORT} — HTTP control port, loopback only (default: 8081)</li>
     *   <li>{@code ICEBRIDGE_ROLE} — FORWARDER, CLIENT, or BOTH (default: FORWARDER)</li>
     *   <li>{@code ICEBRIDGE_IDENTITY_FILE} — path to identity.dat (default: ./identity.dat)</li>
     *   <li>{@code ICEBRIDGE_MAX_PEERS} — max tracked peers (default: 10000)</li>
     *   <li>{@code ICEBRIDGE_PEER_TTL_SEC} — peer eviction TTL (default: 300)</li>
     *   <li>{@code ICEBRIDGE_MAX_QPS_PER_KEY} — rate limit per pub key (default: 30.0)</li>
     *   <li>{@code ICEBRIDGE_BOOTSTRAP} — announce on bootstrap DHT topic (default: true)</li>
     *   <li>{@code ICEBRIDGE_DHT} — embed DHT SessionManager and announce (default: true for
     *       FORWARDER/BOTH, false for CLIENT)</li>
     *   <li>{@code ICEBRIDGE_AUTH_TOKENS_FILE} — path to file with bearer tokens, one per line (default: icebridge-tokens.txt)</li>
     *   <li>{@code ICEBRIDGE_MESH_FANOUT} — N: max IceBridge peers for mesh RELAY (default: 32, LimeWire NUM_CONNECTIONS)</li>
     *   <li>{@code ICEBRIDGE_SEARCH_PEER_FANOUT} — M: max FrostWire peers per search hop (default: 30, MAX_LEAVES)</li>
     *   <li>{@code ICEBRIDGE_MESH_HOP_TTL} — mesh RELAY hop TTL (default: 3, SOFT_MAX)</li>
     *   <li>{@code ICEBRIDGE_SEARCH_TTL} — dual-envelope search TTL (default: 3)</li>
     *   <li>{@code ICEBRIDGE_SOFT_MAX} — hops+remaining_ttl clamp (default: 3)</li>
     *   <li>{@code ICEBRIDGE_LEAF_UP_CONNECTIONS} — leaf attachments to IceBridges (default: 3)</li>
     * </ul>
     *
     * <p>Topology limits are applied via {@link IceBridgeTopology} (process-wide,
     * remote-config ready).
     *
     * @return config built from env vars, with cloud defaults for unset vars
     */
    public static IceBridgeConfig fromEnv() {
        Builder b = newBuilder();
        String host = env("ICEBRIDGE_HOST", "0.0.0.0");
        b.host(host);
        b.rudpPort(envInt("ICEBRIDGE_RUDP_PORT", 6889));
        b.relayPort(envInt("ICEBRIDGE_RELAY_PORT", com.frostwire.search.relay.RelayConstants.RELAY_LISTEN_PORT));
        b.controlHttpPort(envInt("ICEBRIDGE_CONTROL_HTTP_PORT", 8081));
        String roleStr = env("ICEBRIDGE_ROLE", "FORWARDER");
        Role role = Role.valueOf(roleStr.toUpperCase());
        b.role(role);
        String identityPath = env("ICEBRIDGE_IDENTITY_FILE", "identity.dat");
        b.identityFile(new File(identityPath));
        b.maxPeers(envInt("ICEBRIDGE_MAX_PEERS", 10000));
        b.peerTtlSec(envLong("ICEBRIDGE_PEER_TTL_SEC", 300));
        b.maxQpsPerKey(envDouble("ICEBRIDGE_MAX_QPS_PER_KEY", 30.0));
        b.bootstrap(envBool("ICEBRIDGE_BOOTSTRAP", true));
        boolean dhtDefault = role == Role.FORWARDER || role == Role.BOTH;
        b.dhtEnabled(envBool("ICEBRIDGE_DHT", dhtDefault));
        String tokensFile = env("ICEBRIDGE_AUTH_TOKENS_FILE", "icebridge-tokens.txt");
        b.authTokensFile(new File(tokensFile));
        // Topology is process-wide (IceBridgeTopology reads the same env keys
        // in its constructor); re-apply here so fromEnv() after startup still
        // refreshes live limits.
        IceBridgeTopology.get().applyRemote(
                envInt("ICEBRIDGE_MESH_FANOUT", 0),
                envInt("ICEBRIDGE_SEARCH_PEER_FANOUT", 0),
                envInt("ICEBRIDGE_MESH_HOP_TTL", 0),
                envInt("ICEBRIDGE_SEARCH_TTL", 0));
        return b.build();
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        return v != null && !v.isEmpty() ? v : def;
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        if (v == null || v.isEmpty()) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long envLong(String key, long def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        if (v == null || v.isEmpty()) return def;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double envDouble(String key, double def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        if (v == null || v.isEmpty()) return def;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean envBool(String key, boolean def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        if (v == null || v.isEmpty()) return def;
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }
}