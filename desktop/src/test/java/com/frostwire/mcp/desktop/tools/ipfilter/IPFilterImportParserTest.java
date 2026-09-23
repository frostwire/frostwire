/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.mcp.desktop.tools.ipfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import java.util.List;
import org.junit.jupiter.api.Test;

class IPFilterImportParserTest {

  @Test
  void skipsBlankCommentsAndInvalidAddresses() {
    List<IPRange> ranges =
        IPFilterImportTool.validRanges(
            List.of(
                "",
                "# comment",
                "not a range",
                "bad:not-an-ip-1.2.3.4",
                "example:192.0.2.1-192.0.2.20"));

    assertEquals(1, ranges.size());
    assertEquals("192.0.2.1", ranges.get(0).startAddress());
    assertEquals("192.0.2.20", ranges.get(0).endAddress());
  }

  @Test
  void parseLineIgnoresComments() {
    assertNull(IPFilterImportTool.parseLine("# example:192.0.2.1-192.0.2.2"));
    assertTrue(IPFilterImportTool.parseLine("example:192.0.2.1-192.0.2.2") != null);
  }
}
