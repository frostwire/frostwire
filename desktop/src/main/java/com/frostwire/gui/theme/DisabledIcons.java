/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.awt.image.RGBImageFilter;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.plaf.IconUIResource;

/**
 * Disabled ("greyed out") versions of image icons, made on the calling thread.
 *
 * <p>The look and feels make them through Java's image pipeline: a {@code FilteredImageSource}
 * turned into a Toolkit image and wrapped in an {@code ImageIcon}, whose constructor waits on a
 * {@code MediaTracker} while an "Image Fetcher" thread produces the pixels. A disabled button's
 * first paint called that on the EDT and waited on the other thread, which on a busy 2-CPU machine
 * froze the window (Icebase #aa5f47d47ef4, frostwire/frostwire#1516). Here the pixels are filtered
 * directly into a {@link BufferedImage}, which needs no {@code MediaTracker}, with the look and
 * feel's own filter, so the result looks the same.
 */
public final class DisabledIcons {

  private DisabledIcons() {}

  /**
   * {@code icon} with {@code filter} applied to every pixel, keeping HiDPI resolution variants, or
   * null when {@code icon} isn't a fully loaded {@link ImageIcon} (the look and feel's own method
   * handles those).
   */
  public static Icon grayed(Icon icon, RGBImageFilter filter) {
    Image grayed = grayedImage(icon, filter);
    return grayed == null ? null : new IconUIResource(new ImageIcon(grayed));
  }

  /**
   * FlatLaf's disabled image icon, with the filter FlatLaf itself uses: the theme's {@code
   * Component.grayFilter} when it is a pixel filter, else FlatLaf's default for light or dark. Null
   * for icons FlatLaf makes disabled versions of on its own (DisabledIconProvider, SVG).
   */
  public static Icon flatLaf(Icon icon, boolean dark) {
    if (icon instanceof com.formdev.flatlaf.FlatLaf.DisabledIconProvider) {
      return null;
    }
    Object themeFilter = javax.swing.UIManager.get("Component.grayFilter");
    RGBImageFilter filter =
        themeFilter instanceof RGBImageFilter rgb
            ? rgb
            : com.formdev.flatlaf.util.GrayFilter.createDisabledIconFilter(dark);
    return grayed(icon, filter);
  }

  /** The image of {@link #grayed}: BufferedImages, one per resolution variant; or null. */
  static Image grayedImage(Icon icon, RGBImageFilter filter) {
    if (!(icon instanceof ImageIcon imageIcon)
        || imageIcon.getImageLoadStatus() != java.awt.MediaTracker.COMPLETE) {
      return null;
    }
    Image image = imageIcon.getImage();
    if (image instanceof MultiResolutionImage multi) {
      List<Image> variants = multi.getResolutionVariants();
      Image[] grayedVariants = new Image[variants.size()];
      for (int i = 0; i < grayedVariants.length; i++) {
        grayedVariants[i] = filtered(variants.get(i), filter);
        if (grayedVariants[i] == null) {
          return null;
        }
      }
      return new BaseMultiResolutionImage(grayedVariants);
    }
    return filtered(image, filter);
  }

  /** A copy of {@code image} with {@code filter} applied, or null if its size isn't known yet. */
  static BufferedImage filtered(Image image, RGBImageFilter filter) {
    int width = image.getWidth(null);
    int height = image.getHeight(null);
    if (width <= 0 || height <= 0) {
      return null;
    }
    BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = copy.createGraphics();
    try {
      g.drawImage(image, 0, 0, null);
    } finally {
      g.dispose();
    }
    int[] pixels = copy.getRGB(0, 0, width, height, null, 0, width);
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = filter.filterRGB(i % width, i / width, pixels[i]);
    }
    copy.setRGB(0, 0, width, height, pixels, 0, width);
    return copy;
  }
}
