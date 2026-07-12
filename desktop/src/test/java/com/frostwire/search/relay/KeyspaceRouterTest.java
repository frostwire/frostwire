/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyspaceRouterTest {

    @Test
    void keyspaceTargetIsSha1OfNormalizedKeywords() {
        byte[] a = KeyspaceRouter.keyspaceTarget("  Ubuntu ISO ");
        byte[] b = KeyspaceRouter.keyspaceTarget("ubuntu iso");
        assertEquals(20, a.length);
        assertTrue(Arrays.equals(a, b));
    }

    @Test
    void compareXorDistanceOrdersCloserFirst() {
        byte[] target = new byte[20];
        Arrays.fill(target, (byte) 0);
        byte[] close = new byte[20];
        byte[] far = new byte[20];
        Arrays.fill(far, (byte) 0xFF);
        assertTrue(KeyspaceRouter.compareXorDistance(target, close, far) < 0);
        assertTrue(KeyspaceRouter.compareXorDistance(target, far, close) > 0);
    }

    @Test
    void rankByKeyspaceIsDeterministicAndUsesXorDistance() {
        byte[] closePub = new byte[32];
        byte[] farPub = new byte[32];
        Arrays.fill(farPub, (byte) 0xFF);
        PeerDirectory.PeerInfo close =
                new PeerDirectory.PeerInfo(closePub, "c", 1, 1, 0, 0, false, true);
        PeerDirectory.PeerInfo far =
                new PeerDirectory.PeerInfo(farPub, "f", 1, 1, 0, 0, false, true);
        List<PeerDirectory.PeerInfo> ranked =
                KeyspaceRouter.rankByKeyspace("ubuntu", Arrays.asList(far, close));
        assertEquals(2, ranked.size());
        byte[] target = KeyspaceRouter.keyspaceTarget("ubuntu");
        byte[] firstId = KeyspaceRouter.peerKeyspaceId(ranked.get(0).peerPub());
        byte[] secondId = KeyspaceRouter.peerKeyspaceId(ranked.get(1).peerPub());
        assertTrue(KeyspaceRouter.compareXorDistance(target, firstId, secondId) <= 0);
        // Same input → same order
        List<PeerDirectory.PeerInfo> again =
                KeyspaceRouter.rankByKeyspace("ubuntu", Arrays.asList(far, close));
        assertEquals(ranked.get(0).hostname(), again.get(0).hostname());
    }
}
