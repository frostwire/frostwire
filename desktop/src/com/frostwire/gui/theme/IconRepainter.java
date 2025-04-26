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
    public static ImageIcon brightenIfDarkTheme(ImageIcon icon) {
        //if (!(icon instanceof ImageIcon imgIcon)) return icon;

        if (ThemeMediator.getCurrentTheme() == ThemeMediator.ThemeEnum.DEFAULT) {
            return icon; // only modify on dark themes
        }

        Image image = ((ImageIcon) icon).getImage();
        BufferedImage buffered = toBufferedImage(image);

        // Apply a brightness scale (1.2f = 20% brighter, 3f = 300% brighter)
        RescaleOp op = new RescaleOp(4f, 0, null);
        BufferedImage brighter = op.filter(buffered, null);

        return new ImageIcon(brighter);
    }

    private static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) return (BufferedImage) img;
        BufferedImage bimage = new BufferedImage(
                img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();
        return bimage;
    }
}
