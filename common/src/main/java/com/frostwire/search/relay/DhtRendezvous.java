/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TcpEndpoint;

import java.util.ArrayList;

/**
 * BEP 5 rendezvous helper for FrostWire peer and relay discovery.
 *
 * <p>This is intentionally a tiny wrapper around jlibtorrent's real DHT
 * primitives. Many peers can announce under one 20-byte topic hash; mutable
 * DHT items are not used for multi-writer discovery.
 */
public final class DhtRendezvous {
    private DhtRendezvous() {
    }

    public static Sha1Hash peerTopic() {
        return topic(RelayConstants.TOPIC_PEERS);
    }

    public static Sha1Hash relayTopic() {
        return topic(RelayConstants.TOPIC_RELAYS);
    }

    public static Sha1Hash bootstrapTopic() {
        return topic(RelayConstants.TOPIC_BOOTSTRAP);
    }

    public static Sha1Hash topic(String topic) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("topic must be non-empty");
        }
        return new Sha1Hash(RelayConstants.topicHash(topic));
    }

    public static void announcePeer(SessionManager session, int port) {
        announce(session, peerTopic(), port);
    }

    public static void announceRelay(SessionManager session, int port) {
        announce(session, relayTopic(), port);
    }

    public static void announceBootstrap(SessionManager session, int port) {
        announce(session, bootstrapTopic(), port);
    }

    public static void announce(SessionManager session, Sha1Hash topic, int port) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }
        if (topic == null) {
            throw new IllegalArgumentException("topic is null");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        session.dhtAnnounce(topic, port, 0);
    }

    public static ArrayList<TcpEndpoint> findPeers(SessionManager session, int timeoutSeconds) {
        return find(session, peerTopic(), timeoutSeconds);
    }

    public static ArrayList<TcpEndpoint> findRelays(SessionManager session, int timeoutSeconds) {
        return find(session, relayTopic(), timeoutSeconds);
    }

    public static ArrayList<TcpEndpoint> findBootstrapNodes(SessionManager session, int timeoutSeconds) {
        return find(session, bootstrapTopic(), timeoutSeconds);
    }

    public static ArrayList<TcpEndpoint> find(SessionManager session, Sha1Hash topic, int timeoutSeconds) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }
        if (topic == null) {
            throw new IllegalArgumentException("topic is null");
        }
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be > 0");
        }
        return session.dhtGetPeers(topic, timeoutSeconds);
    }
}
