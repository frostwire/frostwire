/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Sha1Hash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DhtRendezvousTest {

    @Test
    void wellKnownTopicsAreSha1AndDistinct() {
        Sha1Hash peers = DhtRendezvous.peerTopic();
        Sha1Hash relays = DhtRendezvous.relayTopic();
        Sha1Hash bootstrap = DhtRendezvous.bootstrapTopic();

        assertEquals(40, peers.toHex().length());
        assertEquals(40, relays.toHex().length());
        assertEquals(40, bootstrap.toHex().length());
        assertNotEquals(peers, relays);
        assertNotEquals(peers, bootstrap);
        assertNotEquals(relays, bootstrap);
    }

    @Test
    void topicIsDeterministic() {
        assertEquals(DhtRendezvous.topic(RelayConstants.TOPIC_RELAYS), DhtRendezvous.relayTopic());
        assertEquals(DhtRendezvous.topic(RelayConstants.TOPIC_PEERS), DhtRendezvous.peerTopic());
    }

    @Test
    void topicRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.topic(null));
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.topic(""));
    }

    @Test
    void announceRejectsInvalidInputBeforeTouchingDht() {
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.announce(null, DhtRendezvous.peerTopic(), 49152));
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.announce(new com.frostwire.jlibtorrent.SessionManager(), null, 49152));
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.announce(new com.frostwire.jlibtorrent.SessionManager(), DhtRendezvous.peerTopic(), -1));
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.announce(new com.frostwire.jlibtorrent.SessionManager(), DhtRendezvous.peerTopic(), 65536));
    }

    @Test
    void findRejectsInvalidInputBeforeTouchingDht() {
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.find(null, DhtRendezvous.peerTopic(), 1));
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.find(new com.frostwire.jlibtorrent.SessionManager(), null, 1));
        assertThrows(IllegalArgumentException.class, () -> DhtRendezvous.find(new com.frostwire.jlibtorrent.SessionManager(), DhtRendezvous.peerTopic(), 0));
    }
}
