package com.frostwire.tests.dht;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.search.relay.IdentityRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(LocalDhtCluster.class)
class IdentityPublishFetchTest {

    @Test
    void testIdentityPublishAndFetch(LocalDhtCluster cluster) throws Exception {
        SessionManager node0 = cluster.getNode(0);
        SessionManager node1 = cluster.getNode(1);

        KeyPairGenerator ed25519Kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair ed25519KeyPair = ed25519Kpg.generateKeyPair();
        byte[] x25519Pub = new byte[32];
        new SecureRandom().nextBytes(x25519Pub);
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);

        IdentityRecord record = IdentityRecord.create(
                nodeId, ed25519KeyPair.getPublic(), x25519Pub, 49152);
        byte[] canonical = record.canonicalBytes();
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(ed25519KeyPair.getPrivate());
        sig.update(canonical);
        record = record.withSignature(sig.sign());

        Map<String, Object> entryMap = new LinkedHashMap<>();
        entryMap.put("v", 1L);
        entryMap.put("node_id", toHex(record.nodeId()));
        entryMap.put("ed25519_pub", Base64.getEncoder().withoutPadding().encodeToString(record.ed25519Pub()));
        entryMap.put("x25519_pub", Base64.getEncoder().withoutPadding().encodeToString(record.x25519Pub()));
        entryMap.put("utp_port", (long) record.utpPort());
        entryMap.put("first_seen", record.firstSeen());
        entryMap.put("last_seen", record.lastSeen());
        entryMap.put("sig", Base64.getEncoder().withoutPadding().encodeToString(record.signature()));
        Entry dhtEntry = Entry.fromMap(entryMap);

        SessionHandle handle0 = new SessionHandle(node0.swig());
        Sha1Hash publishedKey = handle0.dhtPutItem(dhtEntry);
        assertNotNull(publishedKey);
        System.out.println("===PUT=== key: " + publishedKey);

        Thread.sleep(10000);

        SessionHandle handle1 = new SessionHandle(node1.swig());
        CountDownLatch latch = new CountDownLatch(1);
        final Entry[] result = new Entry[1];

        node1.addListener(new AlertListener() {
            @Override
            public int[] types() {
                return new int[] { AlertType.DHT_MUTABLE_ITEM.swig(), AlertType.DHT_IMMUTABLE_ITEM.swig() };
            }

            @Override
            public void alert(Alert<?> alert) {
                if (alert instanceof DhtMutableItemAlert) {
                    result[0] = ((DhtMutableItemAlert) alert).item();
                    latch.countDown();
                } else if (alert instanceof DhtImmutableItemAlert) {
                    result[0] = ((DhtImmutableItemAlert) alert).item();
                    latch.countDown();
                }
            }
        });

        handle1.dhtGetItem(publishedKey);

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Should fetch identity within timeout");
        assertNotNull(result[0], "DHT should return a non-null entry for the published identity key");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
