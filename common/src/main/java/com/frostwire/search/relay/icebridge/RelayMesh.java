/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for linking pure IceBridge FORWARDER nodes into a mesh so
 * multi-hop {@code RELAY} delivery can reach clients registered on
 * different forwarders.
 *
 * <p>Each forwarder is told about every other forwarder's identity and
 * rUDP endpoint via the localhost control {@code /route} API. In
 * production this can be driven by DHT relay discovery; tests wire the
 * mesh explicitly.
 */
public final class RelayMesh {

    private static final Logger LOG = Logger.getLogger(RelayMesh.class);

    private RelayMesh() {
    }

    /**
     * Fully connect {@code n} relays: every node routes every other node
     * as {@link IceBridgeConfig.Role#FORWARDER}.
     *
     * @param nodes control clients + identity + rUDP endpoint of each relay
     * @return number of successful /route calls
     */
    public static int linkFully(List<MeshNode> nodes) {
        if (nodes == null || nodes.size() < 2) {
            return 0;
        }
        int ok = 0;
        for (int i = 0; i < nodes.size(); i++) {
            MeshNode a = nodes.get(i);
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) {
                    continue;
                }
                MeshNode b = nodes.get(j);
                if (a.client.route(b.ed25519Pub, b.host, b.rudpPort, IceBridgeConfig.Role.FORWARDER)) {
                    ok++;
                } else {
                    LOG.warn("RelayMesh: failed to route " + b.label + " into " + a.label);
                }
            }
        }
        return ok;
    }

    /**
     * Descriptor for one mesh participant (typically a pure FORWARDER).
     */
    public static final class MeshNode {
        public final String label;
        public final IceBridgeClient client;
        public final byte[] ed25519Pub;
        public final String host;
        public final int rudpPort;

        public MeshNode(String label,
                        IceBridgeClient client,
                        byte[] ed25519Pub,
                        String host,
                        int rudpPort) {
            if (client == null) {
                throw new IllegalArgumentException("client is null");
            }
            if (ed25519Pub == null || ed25519Pub.length != 32) {
                throw new IllegalArgumentException("ed25519Pub must be 32 bytes");
            }
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("host is blank");
            }
            if (rudpPort <= 0 || rudpPort > 65535) {
                throw new IllegalArgumentException("rudpPort out of range");
            }
            this.label = label != null ? label : "relay";
            this.client = client;
            this.ed25519Pub = ed25519Pub.clone();
            this.host = host;
            this.rudpPort = rudpPort;
        }

        public static List<MeshNode> of(MeshNode... nodes) {
            List<MeshNode> list = new ArrayList<>(nodes.length);
            for (MeshNode n : nodes) {
                if (n != null) {
                    list.add(n);
                }
            }
            return list;
        }
    }
}
