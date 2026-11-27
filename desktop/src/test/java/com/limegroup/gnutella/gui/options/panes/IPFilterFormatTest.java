/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterFormat;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which block list format a downloaded or chosen file is, judged from its first 4 KB. */
class IPFilterFormatTest {
  @TempDir Path tmp;

  private File file(String content) throws Exception {
    Path p = tmp.resolve("list-" + System.nanoTime() + ".txt");
    Files.writeString(p, content);
    return p.toFile();
  }

  @Test
  void aP2PListHasNameColonRangeLines() throws Exception {
    File f =
        file(
            "# comment\n"
                + "Bogon:0.0.0.0-0.255.255.255\n"
                + "Some Org:10.0.0.0-10.255.255.255\n"
                + "Another:192.168.0.0-192.168.255.255\n");
    assertEquals(IPFilterFormat.P2P, IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void aCidrListHasAtLeastThreeBlocks() throws Exception {
    File f = file("1.2.3.0/24\n5.6.0.0/16\n10.0.0.0/8\n");
    assertEquals(IPFilterFormat.CIDR, IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void twoCidrBlocksAreNotEnoughToTellTheFormat() throws Exception {
    File f = file("1.2.3.0/24\n5.6.0.0/16\n");
    assertNull(IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void aHostsFileHasAddressAndNameLines() throws Exception {
    File f =
        file(
            "0.0.0.0 ads.example.com\n"
                + "0.0.0.0 tracker.example.net\n"
                + "127.0.0.1 evil.example.org\n");
    assertEquals(IPFilterFormat.HOSTS, IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void textThatIsNoBlockListHasNoFormat() throws Exception {
    assertNull(IPFilterPaneItem.getIPFilterFileFormat(file("hello\nthis is a story\n")));
    assertNull(IPFilterPaneItem.getIPFilterFileFormat(file("")));
  }

  @Test
  void aMissingFileHasNoFormat() {
    assertNull(IPFilterPaneItem.getIPFilterFileFormat(tmp.resolve("missing.txt").toFile()));
  }

  @Test
  void aListWhoseFirstLinesAreLongCommentsIsStillRecognised() throws Exception {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      text.append("# ").append("x".repeat(60)).append('\n');
    }
    text.append("Bogon:0.0.0.0-0.255.255.255\n")
        .append("Org:10.0.0.0-10.255.255.255\n")
        .append("Other:192.168.0.0-192.168.255.255\n");
    assertEquals(IPFilterFormat.P2P, IPFilterPaneItem.getIPFilterFileFormat(file(text.toString())));
  }
}
