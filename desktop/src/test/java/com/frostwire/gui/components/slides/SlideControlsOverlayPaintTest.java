/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.slides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The promo slide overlay cached an ARGB image as big as itself and ran a 512 MB heap out at
 * startup (Icebase #9792a7e430d2, GitHub #1558). Its size is the slide image's, so it is not
 * bounded. It now paints its two rectangles straight into the clipped Graphics.
 *
 * <p>The overlay's constructor reaches ThemeMediator, which needs a display, so the painting is
 * checked through its helper and the overlay's source is checked for images sized by itself.
 */
class SlideControlsOverlayPaintTest {

  // 40000 x 40000 ARGB pixels is 6.4 GB: any image sized by the overlay runs the heap out.
  private static final int HUGE = 40_000;
  private static final int VIEW = 120;
  private static final int SOCIAL_BAR_HEIGHT = 55;

  @Test
  void aHugeOverlayPaintsIntoASmallGraphicsWithoutAllocating() {
    // paint the overlay's bottom-left corner into a small canvas over white
    BufferedImage canvas = new BufferedImage(VIEW, VIEW, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = canvas.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, VIEW, VIEW);
      g.setClip(0, 0, VIEW, VIEW);
      g.translate(0, VIEW - HUGE);
      SlideControlsOverlay.paintOverlayBackground(
          g,
          HUGE,
          HUGE,
          new Color(45, 52, 58),
          AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
    } finally {
      g.dispose();
    }

    assertEquals(
        Color.BLACK.getRGB(),
        canvas.getRGB(VIEW / 2, VIEW - SOCIAL_BAR_HEIGHT / 2),
        "the social bar is drawn opaque black");
    int fill = canvas.getRGB(VIEW / 2, VIEW - SOCIAL_BAR_HEIGHT - 10);
    // 70% of #2d343a over white
    assertEquals(0x6c, (fill >> 16) & 0xff, 2, "the translucent fill is drawn above the bar");
    assertEquals(0x71, (fill >> 8) & 0xff, 2);
    assertEquals(0x75, fill & 0xff, 2);
  }

  @Test
  void anInvalidSizePaintsNothing() {
    BufferedImage canvas = new BufferedImage(VIEW, VIEW, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = canvas.createGraphics();
    try {
      SlideControlsOverlay.paintOverlayBackground(
          g, -1, Integer.MIN_VALUE, Color.RED, AlphaComposite.SrcOver);
    } finally {
      g.dispose();
    }
    assertEquals(0, canvas.getRGB(VIEW / 2, VIEW / 2));
  }

  @Test
  void theOverlayNeverAllocatesAnImageOfItsOwnSize() throws IOException {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve(
                        "src/main/java/com/frostwire/gui/components/slides/SlideControlsOverlay.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(source.contains("newBufferedImage("), "an image sized by the overlay ran OOM");
    assertFalse(source.contains("createCompatibleImage("));
    assertFalse(source.contains("createVolatileImage("));
    assertTrue(
        source.contains(
            "publicvoidpaint(Graphicsg){paintOverlayBackground(g,getWidth(),getHeight(),"),
        "paint() draws the background straight into its Graphics");
  }
}
