/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RelayFrameTest {

    @Test
    void maxAppPayloadFitsSingleRudpFragment() {
        assertEquals(
                RudpPacket.MAX_FRAGMENT_PAYLOAD - RelayFrame.HEADER_LENGTH,
                RelayFrame.MAX_APP_PAYLOAD);
        assertTrue(RelayFrame.MAX_APP_PAYLOAD > 0);
        assertTrue(RelayFrame.DEFAULT_HOP_TTL <= 3);
    }

    @Test
    void encodeDecodeRoundTrip() {
        byte[] source = new byte[32];
        byte[] target = new byte[32];
        source[0] = 1;
        target[0] = 2;
        byte[] app = "hello-mesh".getBytes();
        byte[] wire = RelayFrame.encode(source, target, 3, app);
        RelayFrame frame = RelayFrame.decode(wire);
        assertArrayEquals(source, frame.sourcePub());
        assertArrayEquals(target, frame.targetPub());
        assertEquals(3, frame.hopTtl());
        assertArrayEquals(app, frame.appPayload());
    }

    @Test
    void encodeRejectsOversizedAppPayload() {
        byte[] source = new byte[32];
        byte[] target = new byte[32];
        byte[] app = new byte[RelayFrame.MAX_APP_PAYLOAD + 1];
        assertThrows(IllegalArgumentException.class,
                () -> RelayFrame.encode(source, target, 1, app));
    }

    @Test
    void decodeRejectsOversizedAppPayload() {
        byte[] wire = new byte[RelayFrame.HEADER_LENGTH + RelayFrame.MAX_APP_PAYLOAD + 1];
        assertThrows(IllegalArgumentException.class, () -> RelayFrame.decode(wire));
    }

    @Test
    void hopTtlClampedToByteRange() {
        byte[] source = new byte[32];
        byte[] target = new byte[32];
        byte[] app = new byte[] {1};
        RelayFrame high = RelayFrame.decode(RelayFrame.encode(source, target, 999, app));
        assertEquals(255, high.hopTtl());
        RelayFrame low = RelayFrame.decode(RelayFrame.encode(source, target, -5, app));
        assertEquals(0, low.hopTtl());
    }
}
