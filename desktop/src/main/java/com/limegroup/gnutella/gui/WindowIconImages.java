/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;

/**
 * The window icon at every size Windows asks for, scaled once, off the EDT.
 *
 * <p>With a single icon image, every time a window moves to another monitor or DPI the JDK's
 * WWindowPeer.updateGC rescales it on the EDT (SunToolkit.getScaledIconImage, Icebase
 * #20253365e913, frostwire/frostwire#1552). With an image at each size the JDK picks an exact match
 * and scales nothing.
 */
final class WindowIconImages {
  /**
   * 16 to 256 px: small icon at 100% to 250% DPI, big icon at 100% to 200% DPI, and the largest.
   */
  static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

  private static volatile List<Image> cached;

  private WindowIconImages() {}

  /** Builds the images (once) from the 256 px theme image. Call it off the EDT. */
  static List<Image> get() {
    List<Image> images = cached;
    if (images == null) {
      synchronized (WindowIconImages.class) {
        images = cached;
        if (images == null) {
          ImageIcon icon = GUIMediator.getThemeImage(GUIConstants.FROSTWIRE_256x256_ICON);
          images = build(icon.getImage(), SIZES);
          cached = images;
        }
      }
    }
    return images;
  }

  /** One image per size, each scaled in halving steps from {@code source}. Thread-safe. */
  static List<Image> build(Image source, int[] sizes) {
    int w = source.getWidth(null);
    int h = source.getHeight(null);
    BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    var g = base.createGraphics();
    g.drawImage(source, 0, 0, null);
    g.dispose();
    List<Image> images = new ArrayList<>(sizes.length);
    for (int size : sizes) {
      images.add(scale(base, size));
    }
    return images;
  }

  private static BufferedImage scale(BufferedImage src, int size) {
    BufferedImage current = src;
    int w = current.getWidth();
    int h = current.getHeight();
    while (w != size || h != size) {
      w = w > size ? Math.max(size, w / 2) : size;
      h = h > size ? Math.max(size, h / 2) : size;
      BufferedImage next = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      var g = next.createGraphics();
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(current, 0, 0, w, h, null);
      g.dispose();
      current = next;
    }
    return current;
  }
}
