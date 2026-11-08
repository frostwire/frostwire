/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileView;
import org.junit.jupiter.api.Test;

/**
 * A native icon (macOS's AquaIcon around a CImage) makes its pixels on its first paint, which for a
 * table cell is on the EDT: CImage.nativeCopyNSImageIntoArray stalled the Library (Icebase
 * #ef2365240d83, frostwire/frostwire#1320). The file views paint each icon once where they look it
 * up, off the EDT, and cache those pixels.
 */
class NativeFileIconPixelsTest {

  /** An icon that, like a native one, does its work when painted. */
  private static final class LazyNativeIcon implements Icon {
    final AtomicInteger paints = new AtomicInteger();

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      paints.incrementAndGet();
      g.fillRect(x, y, 16, 16);
    }

    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 16;
    }
  }

  @Test
  void aLookedUpIconIsCachedAsPixelsSoPaintingItDoesNoNativeWork() {
    LazyNativeIcon nativeIcon = new LazyNativeIcon();
    FileView chooser =
        new FileView() {
          @Override
          public Icon getIcon(File f) {
            return nativeIcon;
          }
        };
    NativeFileIconController.SmartChooserView view =
        new NativeFileIconController.SmartChooserView(chooser);

    Icon looked = view.getIcon(new File("movie.mkv")); // on the IconLoader thread in the app
    int paintsDuringLookup = nativeIcon.paints.get();
    BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int i = 0; i < 5; i++) {
      looked.paintIcon(null, canvas.getGraphics(), 0, 0); // a table cell, on the EDT
    }

    assertEquals(paintsDuringLookup, nativeIcon.paints.get(), "painting reached the native icon");
    assertInstanceOf(ImageIcon.class, looked);
    assertEquals(16, looked.getIconWidth());
    assertEquals(16, looked.getIconHeight());
    assertSame(looked, view.getIcon(new File("movie.mkv")), "the pixels are what is cached");
  }

  @Test
  void anIconThatIsAlreadyPixelsIsKept() {
    ImageIcon pixels = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));

    assertSame(pixels, NativeFileIconController.SmartFileView.rendered(pixels));
    assertSame(null, NativeFileIconController.SmartFileView.rendered(null));
  }
}
