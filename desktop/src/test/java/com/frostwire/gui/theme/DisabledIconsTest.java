/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.plaf.IconUIResource;
import org.junit.jupiter.api.Test;

/**
 * A disabled button's first paint made its grey icon through Java's image pipeline, and the EDT
 * waited on a MediaTracker while an "Image Fetcher" thread produced it, which froze the window on a
 * busy 2-CPU machine (frostwire/frostwire#1516). The grey icon is now filtered directly into a
 * BufferedImage, with the same filter.
 */
class DisabledIconsTest {
  private static final GrayFilter NIMBUS_FILTER = new GrayFilter(true, 50);

  @Test
  void greyIconHasTheSamePixelsAsLookAndFeelGetDisabledIcon() {
    BufferedImage source = sample(6, 4);
    Icon disabled = DisabledIcons.grayed(new ImageIcon(source), NIMBUS_FILTER);

    assertInstanceOf(IconUIResource.class, disabled, "a look-and-feel resource, like the JDK's");
    assertEquals(6, disabled.getIconWidth());
    assertEquals(4, disabled.getIconHeight());
    BufferedImage grey = render(disabled);
    for (int y = 0; y < 4; y++) {
      for (int x = 0; x < 6; x++) {
        assertEquals(
            NIMBUS_FILTER.filterRGB(x, y, source.getRGB(x, y)),
            grey.getRGB(x, y),
            "pixel " + x + "," + y);
      }
    }
  }

  @Test
  void greyImageNeedsNoImageFetcher() {
    // A Toolkit image (what GrayFilter.createDisabledImage returns) makes ImageIcon wait on a
    // MediaTracker for another thread. A BufferedImage is complete as soon as it exists.
    Image grey = DisabledIcons.grayedImage(new ImageIcon(sample(3, 3)), NIMBUS_FILTER);
    assertInstanceOf(BufferedImage.class, grey);
    assertEquals(java.awt.MediaTracker.COMPLETE, new ImageIcon(grey).getImageLoadStatus());
  }

  @Test
  void hiDpiIconsKeepEveryResolution() {
    Image multi = new BaseMultiResolutionImage(sample(4, 4), sample(8, 8));
    ImageIcon icon = new ImageIcon(multi);

    MultiResolutionImage grey =
        assertInstanceOf(
            MultiResolutionImage.class, DisabledIcons.grayedImage(icon, NIMBUS_FILTER));
    assertEquals(2, grey.getResolutionVariants().size());
    BufferedImage large = (BufferedImage) grey.getResolutionVariants().get(1);
    assertEquals(8, large.getWidth());
    assertEquals(NIMBUS_FILTER.filterRGB(5, 5, sample(8, 8).getRGB(5, 5)), large.getRGB(5, 5));
  }

  @Test
  void iconsItCannotHandleAreLeftToTheLookAndFeel() {
    assertNull(DisabledIcons.grayed(null, NIMBUS_FILTER));
    assertNull(
        DisabledIcons.grayed(new IconUIResource(new ImageIcon(sample(2, 2))), NIMBUS_FILTER));
    Image neverLoaded = Toolkit.getDefaultToolkit().createImage(new byte[] {1, 2, 3});
    assertNull(DisabledIcons.grayed(new ImageIcon(neverLoaded), NIMBUS_FILTER));
  }

  @Test
  void everyThemeMakesItsDisabledIconsThisWay() throws Exception {
    Path source = Path.of("src/main/java/com/frostwire/gui/theme/ThemeMediator.java");
    if (!Files.isRegularFile(source)) {
      source = Path.of("desktop").resolve(source);
    }
    String code =
        new String(Files.readAllBytes(source), StandardCharsets.UTF_8).replaceAll("\\s+", "");
    assertTrue(code.contains("UIManager.setLookAndFeel(newSkinNimbusLookAndFeel());"));
    assertTrue(code.contains("FlatLaf.setup(newSkinFlatDarkLaf());"));
    assertTrue(code.contains("FlatLaf.setup(newSkinFlatLightLaf());"));
    assertTrue(!code.contains("newNimbusLookAndFeel(){"), "no anonymous Nimbus subclass left");
    assertTrue(!code.contains("FlatDarkLaf.setup()") && !code.contains("FlatLightLaf.setup()"));
  }

  @Test
  void flatLafUsesItsOwnFilter() {
    BufferedImage source = sample(2, 2);
    Icon disabled = DisabledIcons.flatLaf(new ImageIcon(source), true);
    assertNotNull(disabled);
    assertEquals(
        com.formdev.flatlaf.util.GrayFilter.createDisabledIconFilter(true)
            .filterRGB(1, 1, source.getRGB(1, 1)),
        render(disabled).getRGB(1, 1));
  }

  private static BufferedImage sample(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int alpha = (x + y) % 2 == 0 ? 0xff : 0x80;
        image.setRGB(x, y, (alpha << 24) | (x * 40 << 16) | (y * 60 << 8) | ((x + y) * 20));
      }
    }
    return image;
  }

  private static BufferedImage render(Icon icon) {
    BufferedImage out =
        new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    var g = out.createGraphics();
    g.setComposite(java.awt.AlphaComposite.Src);
    icon.paintIcon(null, g, 0, 0);
    g.dispose();
    return out;
  }
}
