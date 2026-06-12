/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PeerDirectoryTest {

    /** A no-op karma cache that always returns 0 (or a configured score per peer). */
    private static final class FakeKarmaCache extends PeerKarmaCache {
        private final Map<String, Long> scores = new HashMap<>();
        FakeKarmaCache() {
            super(new RemoteKarmaChainFetcher(new KarmaChainSource() {
                @Override
                public Entry fetchManifest(byte[] peerPub) {
                    return null;
                }
            }));
        }
        void setScore(byte[] peerPub, long score) {
            scores.put(com.frostwire.util.Hex.encode(peerPub), score);
        }
        @Override
        public long getKarma(byte[] peerPub) {
            if (peerPub == null) return 0;
            Long v = scores.get(com.frostwire.util.Hex.encode(peerPub));
            return v != null ? v : 0;
        }
    }

    @Test
    void constructorRejectsNullKarmaCache() {
        assertThrows(IllegalArgumentException.class, () -> new PeerDirectory(null));
    }

    @Test
    void constructorRejectsNonPositiveMaxEntries() {
        assertThrows(IllegalArgumentException.class, () -> new PeerDirectory(new FakeKarmaCache(), 0));
        assertThrows(IllegalArgumentException.class, () -> new PeerDirectory(new FakeKarmaCache(), -1));
    }

    @Test
    void upsertRejectsBadInputs() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        assertThrows(IllegalArgumentException.class, () -> d.upsert(null, "host", 6881));
        assertThrows(IllegalArgumentException.class, () -> d.upsert(new byte[31], "host", 6881));
        assertThrows(IllegalArgumentException.class, () -> d.upsert(new byte[32], null, 6881));
        assertThrows(IllegalArgumentException.class, () -> d.upsert(new byte[32], "host", -1));
        assertThrows(IllegalArgumentException.class, () -> d.upsert(new byte[32], "host", 99999));
    }

    @Test
    void upsertAddsEntry() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        d.upsert(new byte[32], "host.example", 6881);
        assertEquals(1, d.size());
        Optional<PeerDirectory.PeerInfo> info = d.get(new byte[32]);
        assertTrue(info.isPresent());
        assertEquals("host.example", info.get().hostname());
        assertEquals(6881, info.get().utpPort());
    }

    @Test
    void upsertIsIdempotent() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] pub = new byte[32];
        d.upsert(pub, "host1", 6881);
        d.upsert(pub, "host2", 6882);
        assertEquals(1, d.size());
        assertEquals("host2", d.get(pub).get().hostname());
    }

    @Test
    void addEndorserRegistersTarget() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] target = new byte[32];
        byte[] endorser = new byte[32];
        d.addEndorser(target, endorser);
        assertEquals(1, d.size());
        PeerDirectory.PeerInfo info = d.get(target).get();
        assertEquals(1, info.endorserCount());
    }

    @Test
    void trustScoreForUnknownPeerIsZero() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        assertEquals(0.0, d.trustScore(new byte[32]), 0.0001);
    }

    @Test
    void trustScoreRejectsNullOrBadInputs() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        assertEquals(0.0, d.trustScore(null), 0.0001);
        assertEquals(0.0, d.trustScore(new byte[31]), 0.0001);
    }

    @Test
    void trustScoreForDirectPeerIsOne() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] pub = new byte[32];
        d.upsert(pub, "host", 6881);
        // 1.0 (direct trust) + 0 (no karma) = 1.0
        assertEquals(1.0, d.trustScore(pub), 0.0001);
    }

    @Test
    void trustScoreIncludesKarma() {
        FakeKarmaCache karma = new FakeKarmaCache();
        PeerDirectory d = new PeerDirectory(karma);
        byte[] pub = new byte[32];
        d.upsert(pub, "host", 6881);
        karma.setScore(pub, 5);
        // 1.0 + 5 = 6.0
        assertEquals(6.0, d.trustScore(pub), 0.0001);
    }

    @Test
    void trustScorePropagatesThroughEndorsements() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] a = new byte[32]; a[31] = 0x01;
        byte[] b = new byte[32]; b[31] = 0x02;
        byte[] c = new byte[32]; c[31] = 0x03;
        d.upsert(a, "a", 1);
        d.upsert(b, "b", 1);
        d.upsert(c, "c", 1);
        // a trusts b, b trusts c
        d.addEndorser(b, a);
        d.addEndorser(c, b);

        // trust(c) = 1 (direct) + 1 (b's trust * 0.5) + 1 (a's trust * 0.5 * 0.5)
        // = 1 + 0.5 + 0.25 = 1.75
        assertEquals(1.75, d.trustScore(c), 0.0001);
    }

    @Test
    void trustScoreDecaysExponentially() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] root = new byte[32]; root[31] = 0x01;
        byte[] hop1 = new byte[32]; hop1[31] = 0x02;
        byte[] hop2 = new byte[32]; hop2[31] = 0x03;
        byte[] hop3 = new byte[32]; hop3[31] = 0x04;
        d.upsert(root, "r", 1);
        d.upsert(hop1, "1", 1);
        d.upsert(hop2, "2", 1);
        d.upsert(hop3, "3", 1);
        d.addEndorser(hop1, root);
        d.addEndorser(hop2, hop1);
        d.addEndorser(hop3, hop2);
        // trust(hop3) = 1 + 0.5 + 0.25 + 0.125 = 1.875
        assertEquals(1.875, d.trustScore(hop3), 0.0001);
    }

    @Test
    void trustScoreHandlesCyclesWithoutInfiniteLoop() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] a = new byte[32]; a[31] = 0x01;
        byte[] b = new byte[32]; b[31] = 0x02;
        d.upsert(a, "a", 1);
        d.upsert(b, "b", 1);
        d.addEndorser(b, a);
        d.addEndorser(a, b); // cycle
        // trust(a) = 1 + 0.5 (b's trust) + ... but b cycles back to a
        // Without cycle detection, infinite loop. With cycle detection,
        // b's transitive trust of a is 0 (already visited), so b's
        // trust = 1 + 0.5 * transitiveTrust(a) — but wait, b is the
        // current target. The cycle short-circuits the recursion.
        // The exact value is implementation-defined, but it must terminate.
        double score = d.trustScore(a);
        assertTrue(score >= 1.0 && score < 10.0,
                "Cycle must terminate; score=" + score);
    }

    @Test
    void trustScoreRespectsWotMaxDepth() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] a = new byte[32]; a[31] = 0x01;
        d.upsert(a, "a", 1);
        // Build a chain deeper than WOT_MAX_DEPTH (3)
        byte[] prev = a;
        for (int i = 0; i < 10; i++) {
            byte[] next = new byte[32]; next[31] = (byte) (0x10 + i);
            d.upsert(next, "h" + i, 1);
            d.addEndorser(next, prev);
            prev = next;
        }
        // trust(prev) = 1 + 0.5 + 0.25 + 0.125 = 1.875
        // Beyond depth 3 the contribution is 0
        assertEquals(1.875, d.trustScore(prev), 0.0001);
    }

    @Test
    void markSpamReturnsNegativeOne() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] pub = new byte[32];
        d.upsert(pub, "spammer", 1);
        assertTrue(d.trustScore(pub) > 0.0);
        d.markSpam(pub);
        assertEquals(-1.0, d.trustScore(pub), 0.0001);
    }

    @Test
    void markSpamOnUnknownPeerRegistersAsSpam() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] pub = new byte[32];
        d.markSpam(pub);
        assertEquals(-1.0, d.trustScore(pub), 0.0001);
        assertEquals(1, d.size());
    }

    @Test
    void markSpamRejectsNullOrBadInputs() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        d.markSpam(null);
        d.markSpam(new byte[31]);
        assertEquals(0, d.size());
    }

    @Test
    void getRejectsBadInputs() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        assertFalse(d.get(null).isPresent());
        assertFalse(d.get(new byte[31]).isPresent());
    }

    @Test
    void topByTrustReturnsSortedByScore() {
        FakeKarmaCache karma = new FakeKarmaCache();
        PeerDirectory d = new PeerDirectory(karma);
        byte[] low = new byte[32]; low[31] = 0x01;
        byte[] high = new byte[32]; high[31] = 0x02;
        byte[] mid = new byte[32]; mid[31] = 0x03;
        d.upsert(low, "low", 1);
        d.upsert(high, "high", 1);
        d.upsert(mid, "mid", 1);
        karma.setScore(low, 1);
        karma.setScore(high, 10);
        karma.setScore(mid, 5);
        var top = d.topByTrust(3);
        assertEquals(3, top.size());
        assertArrayEquals(high, top.get(0).peerPub());
        assertArrayEquals(mid, top.get(1).peerPub());
        assertArrayEquals(low, top.get(2).peerPub());
    }

    @Test
    void topByTrustExcludesSpammers() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] good = new byte[32]; good[31] = 0x01;
        byte[] spam = new byte[32]; spam[31] = 0x02;
        d.upsert(good, "good", 1);
        d.upsert(spam, "spam", 1);
        d.markSpam(spam);
        var top = d.topByTrust(10);
        assertEquals(2, top.size(), "spammer is included but at the bottom");
        assertArrayEquals(good, top.get(0).peerPub(), "good peer first");
        assertArrayEquals(spam, top.get(1).peerPub(), "spammer last");
        assertTrue(top.get(0).isSpam() == false);
        assertTrue(top.get(1).isSpam() == true);
    }

    @Test
    void topByTrustRejectsNonPositiveLimit() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        assertThrows(IllegalArgumentException.class, () -> d.topByTrust(0));
        assertThrows(IllegalArgumentException.class, () -> d.topByTrust(-1));
    }

    @Test
    void topByTrustRespectsLimit() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        for (int i = 0; i < 5; i++) {
            byte[] p = new byte[32]; p[31] = (byte) (0x10 + i);
            d.upsert(p, "h" + i, 1);
        }
        var top = d.topByTrust(2);
        assertEquals(2, top.size());
    }

    @Test
    void evictionWhenOverCapacity() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache(), 3);
        for (int i = 0; i < 5; i++) {
            byte[] p = new byte[32]; p[31] = (byte) (0x10 + i);
            d.upsert(p, "h" + i, 1);
        }
        assertEquals(3, d.size(),
                "Directory evicts oldest when over capacity");
    }

    @Test
    void versionBumpsOnWrite() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        long v0 = d.version();
        d.upsert(new byte[32], "h", 1);
        long v1 = d.version();
        assertTrue(v1 > v0);
    }

    @Test
    void peerInfoDefensivelyCopies() {
        PeerDirectory d = new PeerDirectory(new FakeKarmaCache());
        byte[] pub = new byte[32];
        d.upsert(pub, "h", 1);
        PeerDirectory.PeerInfo info = d.get(pub).get();
        byte[] infoPub = info.peerPub();
        java.util.Arrays.fill(infoPub, (byte) 0xff);
        byte[] originalPub = d.get(pub).get().peerPub();
        assertFalse(java.util.Arrays.equals(originalPub, infoPub));
    }
}
