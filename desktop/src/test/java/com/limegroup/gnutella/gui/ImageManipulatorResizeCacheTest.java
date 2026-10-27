/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

/**
 * IconButton scales its icon and rollover every time it is constructed or its UI is updated. The
 * setup wizard rebuilds its social page each time it is shown, and the scale (area averaging
 * through an ImageProducer and MediaTracker) ran on the EDT every time. A source icon must only be
 * scaled once per size.
 */
class ImageManipulatorResizeCacheTest {

  @Test
  void sameIconAndSizeIsScaledOnce() {
    Icon source = new ImageIcon(new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB));

    Icon first = ImageManipulator.resize(source, 20, 20);
    Icon second = ImageManipulator.resize(source, 20, 20);

    assertSame(first, second, "the second request must reuse the scaled copy");
    assertEquals(20, first.getIconWidth());
    assertEquals(20, first.getIconHeight());
  }

  @Test
  void differentSizesAndSourcesAreScaledSeparately() {
    Icon source = new ImageIcon(new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB));
    Icon other = new ImageIcon(new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB));

    Icon small = ImageManipulator.resize(source, 10, 10);
    Icon large = ImageManipulator.resize(source, 30, 30);

    assertNotSame(small, large);
    assertEquals(30, large.getIconWidth());
    assertNotSame(small, ImageManipulator.resize(other, 10, 10));
  }

  @Test
  void nullIconStaysNull() {
    assertNull(ImageManipulator.resize(null, 10, 10));
  }
}
