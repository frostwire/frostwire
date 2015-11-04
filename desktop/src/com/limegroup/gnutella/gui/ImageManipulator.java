package com.limegroup.gnutella.gui;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;

import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.plaf.IconUIResource;

/**
 * Utility class for manipulating images & icons.
 */
public class ImageManipulator extends RGBImageFilter {
    /**
     * Whether or not we are going to brighten the image.
     */
    private boolean brighter;
    
    /**
     * The percentage to darken or brighten the image.
     */
    private int percent;

    /**
     * Constucts a new manipulator.
     */
    private ImageManipulator(boolean b, int p) {
        brighter = b;
        percent = p;
        canFilterIndexColorModel = true;
    }
    
    /**
     * Filters a pixel.
     */
    public int filterRGB(int x, int y, int rgb) {
        return (rgb & 0xff000000) |
               (filter(rgb >> 16) << 16) |
               (filter(rgb >> 8)  << 8 ) |
               (filter(rgb));
    }
    
    /**
     * Brighens or darkens a single r/g/b value.
     */
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
    
    /**
     * Returns a slightly brighter version of the icon.
     */
    public static Icon brighten(Icon icon) {
        Image img = getImage(icon);
        if(img == null)
            return icon;
        img = brighten(img);
        return new IconUIResource(new ImageIcon(img));
    }
    
    /**
     * Returns a slightly darker version of the icon.
     */
    public static Icon darken(Icon icon) {
        Image img = getImage(icon);
        if(img == null)
            return icon;
        img = darken(img);
        return new IconUIResource(new ImageIcon(img));
    }
    
    /**
     * Returns a grayed version of the icon.
     */
    public static Icon gray(Icon icon) {
        Image img = getImage(icon);
        if(img == null)
            return icon;
        img = gray(img);
        return new IconUIResource(new ImageIcon(img));
    }
    
    /**
     * Manipulates an icon.
     */
    public static Icon manipulate(Icon icon, boolean brighten, int percent) {
        Image img = getImage(icon);
        if(img == null)
            return icon;
        img = manipulate(img, brighten, percent);
        return new IconUIResource(new ImageIcon(img));
    }
    
    /**
     * Brightens an image.
     */
    public static Image brighten(Image img) {
        return manipulate(img, true, 20);
    }
    
    /**
     * Darkens an image.
     */
    public static Image darken(Image img) {
        return manipulate(img, false, 10);
    }
    
    /**
     * Grays an image.
     */
    public static Image gray(Image img) {
        return GrayFilter.createDisabledImage(img);
    }
    
    /**
     * Manipulates an image.
     */
    public static Image manipulate(Image img, boolean brighten, int percent) {
    	ImageFilter filter = new ImageManipulator(brighten, percent);
    	ImageProducer prod = new FilteredImageSource(img.getSource(), filter);
    	return Toolkit.getDefaultToolkit().createImage(prod);
    }
    
    /**
     * Resizes an icon.
     */
    public static Icon resize(Icon icon, int width, int height) {
        Image image = getImage(icon);
        if(image == null)
            return icon;
        image = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }
    
    /**
     * Creates an image from an icon.
     */
    private static Image getImage(Icon icon) {
        if(icon instanceof ImageIcon) {
            return ((ImageIcon)icon).getImage();
        } else {
            BufferedImage buffer = new BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
            Graphics g = buffer.getGraphics();
            icon.paintIcon(new JLabel(), g, 0,0);
            g.dispose();
            return buffer;
        }
    }
}