/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelayConstantsTest {

    @Test
    void topicHashIsSha1LengthAndDeterministic() {
        byte[] a = RelayConstants.topicHash(RelayConstants.TOPIC_RELAYS);
        byte[] b = RelayConstants.topicHash(RelayConstants.TOPIC_RELAYS);
        byte[] c = RelayConstants.topicHash(RelayConstants.TOPIC_PEERS);

        assertEquals(20, a.length);
        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, c));
    }

    @Test
    void defaultsMatchDesignConstraints() {
        assertEquals(30 * 60, RelayConstants.RELAY_REGISTRY_TTL_SEC);
        assertEquals(5 * 60, RelayConstants.IDENTITY_REPUBLISH_INTERVAL_SEC);
        assertEquals(5, RelayConstants.DEFAULT_MAX_QPS);
        assertEquals(3, RelayConstants.WOT_MAX_DEPTH);
        assertEquals(50, RelayConstants.LOCAL_INDEX_MAX_RESULTS);
    }
}
