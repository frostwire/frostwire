/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/**
 * In the dark theme the Library tree renderer brightened its folder icons on every paint (the tree
 * repaints each second), and status bar buttons and labels set a freshly brightened icon on each
 * update. Each call re-ran the filter on the EDT, and a new icon on a disabled button dropped the
 * disabled copy Swing caches, so it was grayed again on the next paint (Icebase #2e9cbdae5e5a). A
 * source icon must only be brightened once per scale factor.
 */
class IconRepainterCacheTest {

  @Test
  void sameIconAndFactorIsBrightenedOnce() {
    Icon source = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));

    Icon first = IconRepainter.brighten(source, 4f);
    Icon second = IconRepainter.brighten(source, 4f);

    assertSame(first, second, "the second request must reuse the brightened copy");
    assertEquals(16, first.getIconWidth());
    assertEquals(16, first.getIconHeight());
  }

  @Test
  void differentFactorsAndSourcesAreBrightenedSeparately() {
    Icon source = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
    Icon other = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));

    Icon strong = IconRepainter.brighten(source, 4f);
    Icon mild = IconRepainter.brighten(source, 1.2f);

    assertNotSame(strong, mild);
    assertNotSame(strong, IconRepainter.brighten(other, 4f));
  }

  @Test
  void disabledButtonKeepsItsGrayedIconWhenTheSameIconIsSetAgain() {
    Icon source = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
    JButton button = new JButton(IconRepainter.brighten(source, 4f));
    button.setEnabled(false);
    Icon grayed = button.getDisabledIcon();

    // what a periodic refresh does: brighten the theme icon again and set it
    button.setIcon(IconRepainter.brighten(source, 4f));

    assertSame(grayed, button.getDisabledIcon(), "the disabled icon must not be grayed again");
  }
}
