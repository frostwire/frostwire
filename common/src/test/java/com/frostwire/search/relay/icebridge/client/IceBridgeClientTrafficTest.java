/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IceBridgeClientTrafficTest {

    @Test
    void readsTheByteCountersFromMetrics() {
        JsonObject metrics = JsonParser.parseString(
                "{\"rudpPacketsIn\":12,\"rudpBytesIn\":123456789012,\"rudpBytesOut\":42,\"rudpPort\":40001}")
                .getAsJsonObject();
        assertEquals(new IceBridgeClient.TrafficBytes(123_456_789_012L, 42), IceBridgeClient.trafficBytes(metrics));
    }

    @Test
    void missingOrMalformedCountersAreUnavailable() {
        assertNull(IceBridgeClient.trafficBytes(null));
        assertNull(IceBridgeClient.trafficBytes(JsonParser.parseString("{\"rudpBytesIn\":1}").getAsJsonObject()));
        assertNull(IceBridgeClient.trafficBytes(
                JsonParser.parseString("{\"rudpBytesIn\":\"x\",\"rudpBytesOut\":2}").getAsJsonObject()));
    }
}
