/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tooltips stay inside the window (no medium-weight native panel) and the icon is prescaled. */
class WindowBoundTooltipsTest {
  private static final Rectangle WINDOW = new Rectangle(100, 100, 800, 600);

  @Test
  void aTooltipThatFitsDoesNotMove() {
    assertEquals(
        new Point(300, 300), WindowBoundTooltips.clamp(300, 300, new Dimension(100, 20), WINDOW));
  }

  @Test
  void aTooltipPastTheRightAndBottomEdgeIsMovedInside() {
    Point p = WindowBoundTooltips.clamp(880, 695, new Dimension(100, 20), WINDOW);
    assertEquals(new Point(800, 680), p);
  }

  @Test
  void aTooltipBeforeTheTopLeftIsMovedInside() {
    assertEquals(
        new Point(100, 100), WindowBoundTooltips.clamp(50, 20, new Dimension(100, 20), WINDOW));
  }

  @Test
  void aTooltipLargerThanTheWindowGoesToItsCorner() {
    assertEquals(
        new Point(100, 100), WindowBoundTooltips.clamp(500, 500, new Dimension(900, 700), WINDOW));
  }

  @Test
  void theFactoryIsInstalledAtStartup() throws Exception {
    String main = Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/Main.java"));
    assertTrue(main.contains("WindowBoundTooltips.install()"));
  }

  @Test
  void theWindowIconHasAnImageAtEverySizeWindowsAsksFor() {
    BufferedImage src = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
    List<Image> images = WindowIconImages.build(src, WindowIconImages.SIZES);
    assertEquals(WindowIconImages.SIZES.length, images.size());
    for (int i = 0; i < images.size(); i++) {
      assertEquals(WindowIconImages.SIZES[i], images.get(i).getWidth(null));
      assertEquals(WindowIconImages.SIZES[i], images.get(i).getHeight(null));
    }
    for (int needed : new int[] {16, 20, 24, 32, 40, 48, 64, 256}) {
      assertTrue(images.stream().anyMatch(i -> i.getWidth(null) == needed), "size " + needed);
    }
  }

  @Test
  void theFrameSetsTheImagesAfterBuildingThemOffTheEdt() throws Exception {
    String src =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/LimeJFrame.java"));
    assertTrue(src.contains("setIconImages(images)"));
    assertTrue(src.contains("BackgroundQueuedExecutorService.schedule(this::loadWindowIcon)"));
  }
}
