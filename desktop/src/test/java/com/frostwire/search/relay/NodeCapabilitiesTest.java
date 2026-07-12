/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeCapabilitiesTest {

    @Test
    void fromRoleMapsRoles() {
        assertTrue(NodeCapabilities.has(NodeCapabilities.fromRole("FORWARDER"),
                NodeCapabilities.RELAY));
        assertFalse(NodeCapabilities.has(NodeCapabilities.fromRole("FORWARDER"),
                NodeCapabilities.SEARCH));
        assertTrue(NodeCapabilities.has(NodeCapabilities.fromRole("CLIENT"),
                NodeCapabilities.SEARCH));
        assertTrue(NodeCapabilities.has(NodeCapabilities.fromRole("BOTH"),
                NodeCapabilities.RELAY | NodeCapabilities.SEARCH));
    }

    @Test
    void toRoleRoundTripsCommonCases() {
        assertEquals("FORWARDER", NodeCapabilities.toRole(NodeCapabilities.DEFAULT_FORWARDER));
        assertEquals("CLIENT", NodeCapabilities.toRole(NodeCapabilities.DEFAULT_PEER));
        assertEquals("BOTH", NodeCapabilities.toRole(NodeCapabilities.DEFAULT_BOTH));
    }
}
