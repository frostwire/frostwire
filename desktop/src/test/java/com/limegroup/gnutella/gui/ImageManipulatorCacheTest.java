/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

/**
 * Brightened and darkened icons are computed once per source icon: every search tab asked for all
 * of them again on the EDT (Icebase #db0a399c0d63).
 */
class ImageManipulatorCacheTest {

  @Test
  void eachSourceIconIsFilteredOnce() {
    Icon icon = new ImageIcon(solid(new Color(100, 100, 100)));
    Icon darker = ImageManipulator.darken(icon);
    Icon brighter = ImageManipulator.brighten(icon);
    assertSame(darker, ImageManipulator.darken(icon));
    assertSame(brighter, ImageManipulator.brighten(icon));
    assertNotSame(darker, brighter);
    // Another icon gets its own copies.
    assertNotSame(darker, ImageManipulator.darken(new ImageIcon(solid(new Color(100, 100, 100)))));
  }

  @Test
  void filtersStillDarkenAndBrighten() {
    Icon icon = new ImageIcon(solid(new Color(100, 100, 100)));
    int gray = red(icon);
    assertTrue(red(ImageManipulator.darken(icon)) < gray, "darken");
    assertTrue(red(ImageManipulator.brighten(icon)) > gray, "brighten");
  }

  private static BufferedImage solid(Color color) {
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    for (int x = 0; x < 4; x++) {
      for (int y = 0; y < 4; y++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    return image;
  }

  private static int red(Icon icon) {
    BufferedImage out = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    icon.paintIcon(null, out.getGraphics(), 0, 0);
    return (out.getRGB(1, 1) >> 16) & 0xff;
  }
}
