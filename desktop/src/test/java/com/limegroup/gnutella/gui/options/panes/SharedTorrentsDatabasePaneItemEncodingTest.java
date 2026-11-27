/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * The main sources are UTF-8 on disk. They used to compile as ISO-8859-1, so every non-ASCII
 * literal became mojibake at runtime (frostwire/frostwire#1644): the em dash printed as three
 * garbage characters.
 */
public class SharedTorrentsDatabasePaneItemEncodingTest {

  private static String formatSize(long bytes) throws Exception {
    Method m = SharedTorrentsDatabasePaneItem.class.getDeclaredMethod("formatSize", long.class);
    m.setAccessible(true);
    return (String) m.invoke(null, bytes);
  }

  @Test
  public void unknownSizeIsOneEmDash() throws Exception {
    assertEquals("—", formatSize(-1));
  }

  @Test
  public void sizesStillFormat() throws Exception {
    assertEquals("12 B", formatSize(12));
  }
}
