/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeshEnvelopeTest {

    @Test
    void wrapUnwrapRoundTripPreservesProtocolAndPayload() {
        byte[] payload = "hello-search".getBytes();
        byte[] wire = MeshEnvelope.wrap(MeshProtocolId.CHAT, payload);
        MeshEnvelope env = MeshEnvelope.unwrap(wire);
        assertEquals(MeshProtocolId.CHAT, env.protocolId());
        assertArrayEquals(payload, env.payload());
        assertEquals("CHAT", MeshProtocolId.name(MeshProtocolId.CHAT));
        assertEquals("TELEMETRY", MeshProtocolId.name(MeshProtocolId.TELEMETRY));
        assertEquals("SEARCH", MeshProtocolId.name(MeshProtocolId.SEARCH));
    }

    @Test
    void searchIsAlsoFramed() {
        byte[] payload = "{\"k\":\"ubuntu\"}".getBytes();
        byte[] wire = MeshEnvelope.encodeForWire(MeshProtocolId.SEARCH, payload);
        assertTrue(wire.length > payload.length);
        MeshEnvelope env = MeshEnvelope.unwrap(wire);
        assertEquals(MeshProtocolId.SEARCH, env.protocolId());
        assertArrayEquals(payload, env.payload());
    }

    @Test
    void unspecifiedProtocolBecomesSearchOnWire() {
        byte[] payload = new byte[] {1, 2, 3};
        byte[] wire = MeshEnvelope.wrap(MeshProtocolId.UNSPECIFIED, payload);
        MeshEnvelope env = MeshEnvelope.unwrap(wire);
        assertEquals(MeshProtocolId.SEARCH, env.protocolId());
        assertArrayEquals(payload, env.payload());
    }

    @Test
    void emptyPayloadRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MeshEnvelope.wrap(MeshProtocolId.SEARCH, new byte[0]));
    }

    @Test
    void barePayloadRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MeshEnvelope.unwrap("not-framed".getBytes()));
    }
}
