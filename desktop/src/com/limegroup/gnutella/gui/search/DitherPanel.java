package com.limegroup.gnutella.gui.search;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JPanel;

/** Simple extension of JPanel that makes a FlowLayout.LEADING JPanel that
 *  has a background image which is painted.
 */
public class DitherPanel extends JPanel {

    /**
     * 
     */
    private static final long serialVersionUID = 6998596665800341964L;
    private final Ditherer DITHERER;
    private boolean isDithering = true;
    private Color nonDitherBackgroundColor;

    /**
     * Creates a FlowLayout.LEADING layout.
     *
     * @param ditherer the <tt>Ditherer</tt> that paints the dithered 
     *  background
     */
    public DitherPanel(Ditherer ditherer, Color nonDitherBackgroundColor) {
        super();
        DITHERER = ditherer;
        this.nonDitherBackgroundColor = nonDitherBackgroundColor;
    }

    /** Does the actual placement of the background image.
     */
    public void paintComponent(java.awt.Graphics g) {
        if (isDithering) { // && !DITHERER.getFromColor().equals(DITHERER.getToColor())) {
            Dimension size = getSize();
            DITHERER.draw(g, size.height, size.width);
        } else {
            if (nonDitherBackgroundColor!=null) {
                Dimension size = getSize();
                g.setColor(nonDitherBackgroundColor);
                g.fillRect(0, 0, size.width, size.height);
            } else {
                super.paintComponent(g);
            }
        }
    }

    public void setDithering(boolean dither) {
        isDithering = dither;
    }

}
