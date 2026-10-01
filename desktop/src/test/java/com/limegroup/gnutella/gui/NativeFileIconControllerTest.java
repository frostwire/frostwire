/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

class NativeFileIconControllerTest {
  @Test
  void extensionLookupsReuseTheSingleNativeViewAndCacheByExtension() {
    CountingFileView view = new CountingFileView();
    NativeFileIconController controller = new NativeFileIconController(view, false);

    Icon first = controller.getIconForExtension("atlas");
    assertNotNull(first);
    assertSame(first, controller.getIconForExtension("ATLAS"));
    controller.getIconForExtension("torrent");

    assertEquals(2, view.iconRequests, "One native lookup per distinct extension");
  }

  private static final class CountingFileView extends NativeFileIconController.SmartFileView {
    private final Icon icon = new ImageIcon();
    private int iconRequests;

    @Override
    public Icon getIcon(File file) {
      iconRequests++;
      return icon;
    }

    @Override
    public boolean isIconCached(File file) {
      return false;
    }

    @Override
    public boolean removeFromCache(File file) {
      return false;
    }
  }
}
