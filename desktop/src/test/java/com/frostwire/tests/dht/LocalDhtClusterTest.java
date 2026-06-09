package com.frostwire.tests.dht;

import com.frostwire.jlibtorrent.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(LocalDhtCluster.class)
class LocalDhtClusterTest {

    @Test
    void testClusterStartsWithDefaultNodes(LocalDhtCluster cluster) {
        assertNotNull(cluster);
        assertEquals(3, cluster.getNodeCount());
        
        SessionManager node0 = cluster.getNode(0);
        assertNotNull(node0);
        assertTrue(cluster.isNodeReady(0));
    }
    
    @Test
    void testAllNodesDhtRunning(LocalDhtCluster cluster) {
        for (int i = 0; i < cluster.getNodeCount(); i++) {
            assertTrue(cluster.isNodeReady(i), "Node " + i + " should have DHT running");
        }
    }
    
    @Test
    void testNodeRetrievalByIndex(LocalDhtCluster cluster) {
        SessionManager node0 = cluster.getNode(0);
        SessionManager node1 = cluster.getNode(1);
        SessionManager node2 = cluster.getNode(2);
        
        assertNotNull(node0);
        assertNotNull(node1);
        assertNotNull(node2);
        
        assertNotSame(node0, node1);
        assertNotSame(node1, node2);
    }
    
    @Test
    void testInvalidNodeIndexThrowsException(LocalDhtCluster cluster) {
        assertThrows(IndexOutOfBoundsException.class, () -> cluster.getNode(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> cluster.getNode(cluster.getNodeCount()));
        assertThrows(IndexOutOfBoundsException.class, () -> cluster.getNode(100));
    }
}
