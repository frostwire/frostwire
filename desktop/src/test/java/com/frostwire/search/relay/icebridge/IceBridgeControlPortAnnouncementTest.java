/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** frostwire/frostwire#1478: the line a child prints to tell its launcher the bound port. */
class IceBridgeControlPortAnnouncementTest {

  @Test
  void parsesTheAnnouncedPortAmongOtherOutput() {
    String output =
        "IceBridge — FrostWire relay servent\n"
            + "  ICEBRIDGE_CONTROL_HTTP_PORT = 0 (TCP)\n"
            + IceBridgeControlPortAnnouncement.line(51234)
            + "\n"
            + "IceBridge is running.\n";
    assertEquals(51234, IceBridgeControlPortAnnouncement.parse(output));
  }

  @Test
  void aLineStillBeingWrittenIsNotReadAsAShorterPort() {
    assertEquals(0, IceBridgeControlPortAnnouncement.parse(""));
    assertEquals(0, IceBridgeControlPortAnnouncement.parse("starting\nICEBRIDGE_CONTROL_PORT=51"));
    assertEquals(51234, IceBridgeControlPortAnnouncement.parse("ICEBRIDGE_CONTROL_PORT=51234\r\n"));
  }

  @Test
  void onlyALineThatStartsWithThePrefixCounts() {
    assertEquals(0, IceBridgeControlPortAnnouncement.parse("  ICEBRIDGE_CONTROL_PORT=1234\n"));
    assertEquals(
        1234,
        IceBridgeControlPortAnnouncement.parse(
            "ICEBRIDGE_CONTROL_PORT=1234\nICEBRIDGE_CONTROL_PORT=4321\n"));
  }

  @Test
  void aCompleteLineWithoutAValidPortIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeControlPortAnnouncement.parse("ICEBRIDGE_CONTROL_PORT=\n"));
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeControlPortAnnouncement.parse("ICEBRIDGE_CONTROL_PORT=http\n"));
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeControlPortAnnouncement.parse("ICEBRIDGE_CONTROL_PORT=0\n"));
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeControlPortAnnouncement.parse("ICEBRIDGE_CONTROL_PORT=65536\n"));
    assertThrows(IllegalArgumentException.class, () -> IceBridgeControlPortAnnouncement.line(0));
  }
}
