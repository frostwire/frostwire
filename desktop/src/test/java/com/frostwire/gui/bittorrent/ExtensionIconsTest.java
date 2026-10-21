/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

class ExtensionIconsTest {

  /**
   * The Partial Files dialog asked Windows' shell for each new file type's icon while painting,
   * blocking the EDT for over 5s in Win32ShellFolderManager2$ComInvoker (Icebase #09cc878c98f7).
   */
  @Test
  void uncachedIconsLoadInTheBackgroundOncePerExtension() {
    Icon mp3 = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
    List<Runnable> queued = new ArrayList<>();
    AtomicInteger lookups = new AtomicInteger();
    AtomicInteger repaints = new AtomicInteger();
    ExtensionIcons icons =
        new ExtensionIcons(
            extension -> {
              lookups.incrementAndGet();
              if ("bad".equals(extension)) {
                throw new IllegalStateException("shell failure");
              }
              return "mp3".equals(extension) ? mp3 : null;
            },
            queued::add,
            repaints::incrementAndGet);

    assertNull(icons.get("mp3"), "the painting thread must not look the icon up");
    assertNull(icons.get("mp3"));
    assertNull(icons.get("bad"));
    assertEquals(0, lookups.get());
    assertEquals(2, queued.size(), "one load per extension");

    queued.forEach(Runnable::run);
    assertSame(mp3, icons.get("mp3"));
    assertNull(icons.get("bad"), "a failed lookup is remembered as no icon");
    assertEquals(2, lookups.get());
    assertEquals(2, repaints.get());
    assertEquals(2, queued.size(), "loaded and failed types are not queued again");
  }
}
