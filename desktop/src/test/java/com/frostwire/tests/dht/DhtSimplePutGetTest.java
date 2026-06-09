package com.frostwire.tests.dht;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(LocalDhtCluster.class)
class DhtSimplePutGetTest {

    @Test
    void testDhtSimplePutAndGet(LocalDhtCluster cluster) throws Exception {
        SessionManager node0 = cluster.getNode(0);
        SessionManager node1 = cluster.getNode(1);
        
        // Create entry 
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("key1", "value1");
        itemMap.put("key2", 42L);
        Entry item = Entry.fromMap(itemMap);
        
        // Use SessionHandle to put the item (uses session's DHT keypair)
        SessionHandle handle0 = new SessionHandle(node0.swig());
        Sha1Hash itemKey = handle0.dhtPutItem(item);
        
        assertNotNull(itemKey, "Put should return a key");
        
        // Wait for DHT propagation
        Thread.sleep(5000);
        
        // Fetch from node1 using the returned key
        SessionHandle handle1 = new SessionHandle(node1.swig());
        CountDownLatch fetchLatch = new CountDownLatch(1);
        final Entry[] result = new Entry[1];
        
        node1.addListener(new AlertListener() {
            @Override
            public int[] types() {
                return new int[] { AlertType.DHT_MUTABLE_ITEM.swig(), AlertType.DHT_IMMUTABLE_ITEM.swig() };
            }
            
            @Override
            public void alert(Alert<?> alert) {
                if (alert instanceof DhtMutableItemAlert) {
                    DhtMutableItemAlert itemAlert = (DhtMutableItemAlert) alert;
                    result[0] = itemAlert.item();
                    fetchLatch.countDown();
                } else if (alert instanceof DhtImmutableItemAlert) {
                    DhtImmutableItemAlert itemAlert = (DhtImmutableItemAlert) alert;
                    result[0] = itemAlert.item();
                    fetchLatch.countDown();
                }
            }
        });
        
        handle1.dhtGetItem(itemKey);
        
        assertTrue(fetchLatch.await(20, java.util.concurrent.TimeUnit.SECONDS), 
            "Should fetch item within timeout");
        assertNotNull(result[0], "Fetched entry should not be null");
        
        // Debug: print the fetched entry
        System.out.println("Fetched entry toString: " + result[0].toString());
    }
}
