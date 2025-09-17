package com.frostwire.gui.theme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

/**
 * We want to repaint icons when the theme changes to Dark,
 * don't want to redesign all the icons, maybe we can paint them lighter
 */
public class IconRepainter {
    /**
     * Brightens the icon if the current theme is not the default one.
     * It uses a default scale factor of 4f to make the icon pretty much white.
     *
     * @param original The original icon to be brightened.
     * @return A new ImageIcon that is brightened if the current theme is not the default one.
     */
    public static Icon brightenIfDarkTheme(Icon original) {
        return brightenIfDarkTheme(original, 4f);
    }


    /**
     * Brightens the icon if the current theme is not the default one.
     * The scale factor determines how much brighter the icon will be.
     * 1.2f = 20% brighter, 3f = 300% brighter.
     *
     * @param original    The original icon to be brightened.
     * @param scaleFactor The scale factor for brightness adjustment.
     * @return A new ImageIcon that is brightened if the current theme is not the default one.
     */
    public static Icon brightenIfDarkTheme(Icon original, float scaleFactor) {
        if (original == null || !ThemeMediator.isDarkLafThemeOn()) {
            if (original instanceof ImageIcon) {
                return original;
            }
            // Always ensure the returned icon is an ImageIcon
            return toImageIcon(original);
        }

        BufferedImage buffered = toBufferedImage(original);

        // Apply a brightness scale (1.2f = 20% brighter, 3f = 300% brighter)
        RescaleOp op = new RescaleOp(scaleFactor, 0, null);
        BufferedImage brighter = op.filter(buffered, null);

        return new ImageIcon(brighter);
    }

    public static BufferedImage toBufferedImage(Icon icon) {
        BufferedImage bimage = new BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
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
