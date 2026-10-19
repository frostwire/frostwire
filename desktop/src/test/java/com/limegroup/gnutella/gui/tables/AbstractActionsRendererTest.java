/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AbstractActionsRendererTest {

  /** Counts Component.hide(), which updates the native cursor on Windows. */
  private static final class CountingLabel extends JLabel {
    final AtomicInteger hides = new AtomicInteger();

    @Override
    @SuppressWarnings("deprecation")
    public void hide() {
      hides.incrementAndGet();
      super.hide();
    }
  }

  /**
   * Rows that can't play or download used to hide those labels, so painting a table with mixed rows
   * hid and showed them once per row (Icebase #5e0086bf886b).
   */
  @Test
  void actionsThatDoNotApplyClearTheIconWithoutHidingTheLabel() throws Exception {
    Icon icon = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
    SwingUtilities.invokeAndWait(
        () -> {
          CountingLabel label = new CountingLabel();
          for (int row = 0; row < 10; row++) {
            boolean applies = row % 2 == 0;
            AbstractActionsRenderer.showAction(label, applies, icon);
            if (applies) {
              assertSame(icon, label.getIcon());
            } else {
              assertNull(label.getIcon());
            }
            assertTrue(label.isVisible());
          }
          assertTrue(label.hides.get() == 0, "hide() ran " + label.hides.get() + " times");
        });
  }
}
