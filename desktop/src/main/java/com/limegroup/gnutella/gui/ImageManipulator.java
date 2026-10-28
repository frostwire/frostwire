package com.limegroup.gnutella.gui;

import java.awt.*;
import java.awt.image.*;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import javax.swing.plaf.IconUIResource;

/** Utility class for manipulating images and icons. */
public class ImageManipulator extends RGBImageFilter {
  /**
   * Scaled copies by source icon and target size. Source icons come from shared caches (such as
   * IconManager's button icons), so components that are rebuilt, like setup wizard pages, reuse the
   * scaled copy instead of area-averaging the image again on the EDT. Weak keys let a dropped
   * source icon (theme or icon-size switch) take its copies with it.
   */
  private static final Map<Icon, Map<Dimension, Icon>> RESIZED =
      Collections.synchronizedMap(new WeakHashMap<>());

  /** Whether or not we are going to brighten the image. */
  private final boolean brighter;

  /** The percentage to darken or brighten the image. */
  private final int percent;

  /** Constructs a new manipulator. */
  private ImageManipulator(boolean b, int p) {
    brighter = b;
    percent = p;
    canFilterIndexColorModel = true;
  }

  /**
   * Brightened and darkened copies, cached per source icon like {@link #resize}: each is a pixel
   * filter whose ImageIcon waits for the filtered image, and every search tab asked for all of them
   * again on the EDT (Icebase #db0a399c0d63). Shared between callers, so not to be modified.
   */
  private static final Map<Icon, Icon> BRIGHTENED =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static final Map<Icon, Icon> DARKENED = Collections.synchronizedMap(new WeakHashMap<>());

  /** Returns a slightly brighter version of the icon. */
  public static Icon brighten(Icon icon) {
    if (icon == null) {
      return null;
    }
    return BRIGHTENED.computeIfAbsent(icon, i -> filtered(i, true));
  }

  /** Returns a slightly darker version of the icon. */
  public static Icon darken(Icon icon) {
    if (icon == null) {
      return null;
    }
    return DARKENED.computeIfAbsent(icon, i -> filtered(i, false));
  }

  private static Icon filtered(Icon icon, boolean brighter) {
    Image img = getImage(icon);
    if (img == null) return icon;
    img = brighter ? brighten(img) : darken(img);
    return new IconUIResource(new ImageIcon(img));
  }

  /** Brightens an image. */
  private static Image brighten(Image img) {
    return manipulate(img, true, 20);
  }

  /** Darkens an image. */
  private static Image darken(Image img) {
    return manipulate(img, false, 10);
  }

  /** Manipulates an image. */
  private static Image manipulate(Image img, boolean brighten, int percent) {
    ImageFilter filter = new ImageManipulator(brighten, percent);
    ImageProducer prod = new FilteredImageSource(img.getSource(), filter);
    return Toolkit.getDefaultToolkit().createImage(prod);
  }

  /**
   * Returns {@code icon} scaled to {@code width} x {@code height}. The result is cached per source
   * icon and size and shared between callers, so it must not be modified.
   */
  public static Icon resize(Icon icon, int width, int height) {
    if (icon == null) {
      return null;
    }
    Map<Dimension, Icon> sizes = RESIZED.computeIfAbsent(icon, k -> new ConcurrentHashMap<>());
    return sizes.computeIfAbsent(new Dimension(width, height), d -> scale(icon, width, height));
  }

  private static Icon scale(Icon icon, int width, int height) {
    Image image = getImage(icon);
    if (image == null) return icon;
    image = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(image);
  }

  //    /**
  //     * Grays an image.
  //     */
  //    public static Image gray(Image img) {
  //        return GrayFilter.createDisabledImage(img);
  //    }

  /** Creates an image from an icon. */
  private static Image getImage(Icon icon) {
    if (icon instanceof ImageIcon) {
      return ((ImageIcon) icon).getImage();
    } else {
      BufferedImage buffer =
          new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics g = buffer.getGraphics();
      icon.paintIcon(new JLabel(), g, 0, 0);
      g.dispose();
      return buffer;
    }
  }

  /** Filters a pixel. */
  public int filterRGB(int x, int y, int rgb) {
    return (rgb & 0xff000000) | (filter(rgb >> 16) << 16) | (filter(rgb >> 8) << 8) | (filter(rgb));
  }

  /** Brightens or darkens a single r/g/b value. */
  private int filter(int color) {
    color = color & 0xff;
    if (brighter) {
      color = (255 - ((255 - color) * (100 - percent) / 100));
    } else {
      color = (color * (100 - percent) / 100);
    }
    if (color < 0) color = 0;
    if (color > 255) color = 255;
    return color;
  }
}
