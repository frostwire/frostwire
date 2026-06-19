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
    private final int controlHttpPort;
    private final boolean controlStdio;
    private final Role role;
    private final File identityFile;
    private final int maxPeers;
    private final long peerTtlSec;
    private final double maxQpsPerKey;
    private final boolean bootstrap;

    public IceBridgeConfig(String host,
                           int rudpPort,
                           int controlHttpPort,
                           boolean controlStdio,
                           Role role,
                           File identityFile,
                           int maxPeers,
                           long peerTtlSec,
                           double maxQpsPerKey,
                           boolean bootstrap) {
        this.host = Objects.requireNonNullElse(host, "0.0.0.0");
        this.rudpPort = requirePositiveOrZero(rudpPort, "rudpPort");
        this.controlHttpPort = controlHttpPort;
        this.controlStdio = controlStdio;
        this.role = Objects.requireNonNullElse(role, Role.CLIENT);
        this.identityFile = identityFile;
        this.maxPeers = requirePositive(maxPeers, "maxPeers");
        this.peerTtlSec = requirePositive(peerTtlSec, "peerTtlSec");
        this.maxQpsPerKey = requirePositive(maxQpsPerKey, "maxQpsPerKey");
        this.bootstrap = bootstrap;
        if (controlHttpPort <= 0 && !controlStdio) {
            throw new IllegalArgumentException("At least one control channel must be enabled");
        }
    }

    public String host() {
        return host;
    }

    public int rudpPort() {
        return rudpPort;
    }

    public int controlHttpPort() {
        return controlHttpPort;
    }

    public boolean controlStdio() {
        return controlStdio;
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

    public boolean canAcceptIncoming() {
        return role == Role.FORWARDER || role == Role.BOTH;
    }

    /** Default config for a local daemon controlled by FrostWire. */
    public static IceBridgeConfig localDefaults() {
        return newBuilder()
                .host("127.0.0.1")
                .rudpPort(0)
                .controlHttpPort(8797)
                .controlStdio(false)
                .role(Role.BOTH)
                .maxPeers(1000)
                .peerTtlSec(120)
                .maxQpsPerKey(5.0)
                .build();
    }

    /** Default config for a headless cloud forwarder. */
    public static IceBridgeConfig cloudDefaults() {
        return newBuilder()
                .rudpPort(6888)
                .controlHttpPort(8080)
                .controlStdio(false)
                .role(Role.FORWARDER)
                .maxPeers(10000)
                .peerTtlSec(120)
                .maxQpsPerKey(10.0)
                .bootstrap(true)
                .build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "0.0.0.0";
        private int rudpPort;
        private int controlHttpPort;
        private boolean controlStdio;
        private Role role = Role.CLIENT;
        private File identityFile;
        private int maxPeers = 1000;
        private long peerTtlSec = 120;
        private double maxQpsPerKey = 5.0;
        private boolean bootstrap;

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

        public Builder controlHttpPort(int controlHttpPort) {
            this.controlHttpPort = controlHttpPort;
            return this;
        }

        public Builder controlStdio(boolean controlStdio) {
            this.controlStdio = controlStdio;
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

        public IceBridgeConfig build() {
            return new IceBridgeConfig(host, rudpPort, controlHttpPort, controlStdio, role,
                    identityFile, maxPeers, peerTtlSec, maxQpsPerKey, bootstrap);
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
}