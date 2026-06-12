/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IdentityRecordPublisherTest {

    private static IdentityKeys identity;
    private static int utpPort;
    private static final byte[] SALT_BYTES =
            IdentityRecord.BEP46_SALT.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @BeforeAll
    static void setUpClass() throws Exception {
        identity = IdentityKeys.generate(4);
        utpPort = 6888;
    }

    private RecordingSession session;
    private IdentityRecordPublisher publisher;

    @BeforeEach
    void setUp() {
        session = new RecordingSession();
        publisher = new IdentityRecordPublisher(identity, utpPort);
    }

    @Test
    void constructorRejectsNullAndBadPort() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityRecordPublisher(null, 6888));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityRecordPublisher(identity, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityRecordPublisher(identity, 99999));
    }

    @Test
    void publishIfNeededReturnsZeroForNullSession() {
        assertEquals(0, publisher.publishIfNeeded(null));
        assertEquals(0, publisher.lastPublishEpochSec());
    }

    @Test
    void publishCallsDhtPutItemWithCorrectKeyAndSalt() {
        int count = publisher.publish(session);
        assertEquals(1, count);
        assertEquals(1, session.putItemCalls.size());

        Object[] call = session.putItemCalls.get(0);
        // call: { pubKey, privKey, entry, salt, utpPort }
        assertArrayEquals(identity.ed25519PubRaw(), (byte[]) call[0]);
        assertArrayEquals(identity.ed25519SecretKeyNaCl(), (byte[]) call[1]);
        assertArrayEquals(SALT_BYTES, (byte[]) call[3]);
        assertEquals(utpPort, (int) call[4]);
    }

    @Test
    void publishedRecordVerifiesAgainstCanonical() {
        publisher.publish(session);
        // The entry should be reconstructable as a valid IdentityRecord
        // whose signature verifies against the publisher's pubkey.
        Entry entry = (Entry) session.putItemCalls.get(0)[2];
        IdentityRecord record = IdentityRecord.fromEntry(entry);
        assertEquals(utpPort, record.utpPort());
        assertArrayEquals(identity.ed25519PubRaw(), record.ed25519Pub());
        assertArrayEquals(identity.x25519PubRaw(), record.x25519Pub());
        assertArrayEquals(identity.nodeId(), record.nodeId());
        assertTrue(record.verifySignature());
    }

    @Test
    void publishUpdatesLastPublishTimestamp() {
        assertEquals(0L, publisher.lastPublishEpochSec());
        publisher.publish(session);
        long ts1 = publisher.lastPublishEpochSec();
        assertTrue(ts1 > 0);
    }

    @Test
    void publishIfNeededThrottlesOnSubsequentCalls() {
        // First call publishes
        assertEquals(1, publisher.publishIfNeeded(session));
        long ts1 = publisher.lastPublishEpochSec();
        // Second call within the throttle window is a no-op
        assertEquals(0, publisher.publishIfNeeded(session));
        assertEquals(ts1, publisher.lastPublishEpochSec());
    }

    @Test
    void publishForcesRepublishIgnoringThrottle() {
        publisher.publish(session);
        long ts1 = publisher.lastPublishEpochSec();
        // Force a re-publish via the explicit method
        assertEquals(1, publisher.publish(session));
        long ts2 = publisher.lastPublishEpochSec();
        assertTrue(ts2 >= ts1);
        assertEquals(2, session.putItemCalls.size());
    }

    @Test
    void publishReturnsZeroWhenSessionThrows() {
        ThrowingSession throwing = new ThrowingSession();
        assertEquals(0, publisher.publish(throwing));
    }

    @Test
    void publishIfNeededReturnsZeroWhenSessionThrows() {
        ThrowingSession throwing = new ThrowingSession();
        assertEquals(0, publisher.publishIfNeeded(throwing));
    }

    @Test
    void accessorsReturnConstructorValues() {
        assertEquals(utpPort, publisher.utpPort());
    }

    // --- helpers ---

    private static final class RecordingSession extends SessionManager {
        final List<Object[]> putItemCalls = new ArrayList<>();
        // Each entry: { pubKey, privKey, entry, salt, utpPort }

        @Override
        public Sha1Hash dhtPutItem(Entry entry) {
            return null;
        }

        @Override
        public void dhtPutItem(byte[] publicKey, byte[] privateKey, Entry entry, byte[] salt) {
            int port = -1;
            try {
                java.util.Map<String, Entry> dict = entry.dictionary();
                port = (int) dict.get("utp_port").integer();
            } catch (Throwable ignored) {
            }
            putItemCalls.add(new Object[]{publicKey, privateKey, entry, salt, port});
        }
    }

    private static final class ThrowingSession extends SessionManager {
        @Override
        public Sha1Hash dhtPutItem(Entry entry) {
            return null;
        }

        @Override
        public void dhtPutItem(byte[] publicKey, byte[] privateKey, Entry entry, byte[] salt) {
            throw new RuntimeException("simulated DHT failure");
        }
    }
}
