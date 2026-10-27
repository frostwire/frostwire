package com.frostwire.gui.theme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

/**
 * We want to repaint icons when the theme changes to Dark, don't want to redesign all the icons,
 * maybe we can paint them lighter
 */
public class IconRepainter {
  /**
   * Brightened copies by source icon and scale factor. Callers brighten from renderers that run on
   * every paint (the Library tree repaints each second) and from refresh code that sets the result
   * on a button or label; a fresh copy each time re-ran the filter on the EDT and, set on a button,
   * dropped the disabled icon Swing caches for it. Weak keys let a dropped source icon take its
   * copies with it.
   */
  private static final Map<Icon, Map<Float, Icon>> BRIGHTENED =
      Collections.synchronizedMap(new WeakHashMap<>());

  /**
   * Brightens the icon if the current theme is not the default one. It uses a default scale factor
   * of 4f to make the icon pretty much white.
   *
   * @param original The original icon to be brightened.
   * @return A new ImageIcon that is brightened if the current theme is not the default one.
   */
  public static Icon brightenIfDarkTheme(Icon original) {
    return brightenIfDarkTheme(original, 4f);
  }

  /**
   * Brightens the icon if the current theme is not the default one. The scale factor determines how
   * much brighter the icon will be. 1.2f = 20% brighter, 3f = 300% brighter.
   *
   * @param original The original icon to be brightened.
   * @param scaleFactor The scale factor for brightness adjustment.
   * @return An ImageIcon that is brightened if the current theme is not the default one. It is
   *     shared between callers, so it must not be modified.
   */
  public static Icon brightenIfDarkTheme(Icon original, float scaleFactor) {
    if (original == null || !ThemeMediator.isDarkLafThemeOn()) {
      if (original instanceof ImageIcon) {
        return original;
      }
      // Always ensure the returned icon is an ImageIcon
      return toImageIcon(original);
    }
    return brighten(original, scaleFactor);
  }

  /**
   * Returns {@code original} brightened by {@code scaleFactor}, filtered once per icon and factor.
   */
  static Icon brighten(Icon original, float scaleFactor) {
    Map<Float, Icon> copies = BRIGHTENED.computeIfAbsent(original, k -> new ConcurrentHashMap<>());
    return copies.computeIfAbsent(scaleFactor, f -> filter(original, f));
  }

  private static Icon filter(Icon original, float scaleFactor) {
    BufferedImage buffered = toBufferedImage(original);

    // Apply a brightness scale (1.2f = 20% brighter, 3f = 300% brighter)
    RescaleOp op = new RescaleOp(scaleFactor, 0, null);
    BufferedImage brighter = op.filter(buffered, null);

    return new ImageIcon(brighter);
  }

  public static BufferedImage toBufferedImage(Icon icon) {
    BufferedImage bimage =
        new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D bufferedImageGraphics = bimage.createGraphics();
    icon.paintIcon(null, bufferedImageGraphics, 0, 0);
    bufferedImageGraphics.dispose();
    return bimage;
  }

  public static ImageIcon toImageIcon(Icon icon) {
    if (icon instanceof ImageIcon) {
      return (ImageIcon) icon;
    }
    return new ImageIcon(toBufferedImage(icon));
  }
}
