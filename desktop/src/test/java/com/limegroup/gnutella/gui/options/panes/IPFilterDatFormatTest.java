/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.limegroup.gnutella.gui.options.panes.ipfilter.DatFilterInputStreamReader;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPFilterFormat;
import com.limegroup.gnutella.gui.options.panes.ipfilter.IPRange;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** eMule ipfilter.dat files are detected as DAT, not HOSTS (frostwire/frostwire#1652). */
class IPFilterDatFormatTest {
  private static final String EMULE =
      "000.000.000.000 - 000.255.255.255 , 000 , Bogon\n"
          + "010.000.000.000 - 010.255.255.255 , 000 , Private\n"
          + "192.168.000.000 - 192.168.255.255 , 100 , LAN\n";

  @Test
  void emuleDatFileIsDetectedAsDat(@TempDir Path dir) throws Exception {
    File f = dir.resolve("ipfilter.dat").toFile();
    Files.writeString(f.toPath(), EMULE);
    assertEquals(IPFilterFormat.DAT, IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void hostsFileIsStillHosts(@TempDir Path dir) throws Exception {
    File f = dir.resolve("hosts").toFile();
    Files.writeString(f.toPath(), "0.0.0.0 a.com\n0.0.0.0 b.com\n1.2.3.4 c.com\n");
    assertEquals(IPFilterFormat.HOSTS, IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void twoLinesAreNotEnough(@TempDir Path dir) throws Exception {
    File f = dir.resolve("x.dat").toFile();
    Files.writeString(f.toPath(), "garbage\n000.000.000.000 - 000.255.255.255 , 000 , Bogon\n");
    assertNull(IPFilterPaneItem.getIPFilterFileFormat(f));
  }

  @Test
  void datReaderParsesEmuleLines(@TempDir Path dir) throws Exception {
    File f = dir.resolve("ipfilter.dat").toFile();
    Files.writeString(f.toPath(), "# comment\n" + EMULE);
    DatFilterInputStreamReader reader = new DatFilterInputStreamReader(f);
    try {
      IPRange first = reader.readLine();
      assertEquals("0.0.0.0", first.startAddress());
      assertEquals("0.255.255.255", first.endAddress());
      assertEquals("Bogon", first.description());
      assertEquals("10.0.0.0", reader.readLine().startAddress());
      IPRange third = reader.readLine();
      assertEquals("192.168.0.0", third.startAddress());
      assertEquals("LAN", third.description());
      assertNull(reader.readLine());
    } finally {
      reader.close();
    }
  }
}
