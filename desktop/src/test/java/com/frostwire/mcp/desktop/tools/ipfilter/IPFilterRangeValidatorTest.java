/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.tools.ipfilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import org.junit.jupiter.api.Test;

class IPFilterRangeValidatorTest {

  @Test
  void rejectsInvalidLiteralBeforeTheAddToolTouchesTheTableOrNativeFilter() {
    JsonObject args = new JsonObject();
    args.addProperty("start", "not-an-ip-address");
    args.addProperty("end", "192.0.2.20");

    JsonObject result = new IPFilterAddTool().execute(args);

    assertTrue(result.has("error"));
    assertFalse(result.has("added"));
  }

  @Test
  void validatesBothAddressEndpointsWithTheNativeLiteralParser() {
    IPFilterRangeValidator.parse(new IPRange("ok", "192.0.2.1", "192.0.2.20"));
    assertThrows(
        IllegalArgumentException.class,
        () -> IPFilterRangeValidator.parse(new IPRange("bad", "192.0.2.1", "not-an-ip")));
  }
}
