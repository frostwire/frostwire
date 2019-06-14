/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.theme;

import javax.imageio.ImageIO;
import javax.swing.plaf.nimbus.AbstractRegionPainter;
import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractSkinPainter extends AbstractRegionPainter {
    private static final String IMAGES_PATH = "org/limewire/gui/images/skin_";
    protected final ShapeGenerator shapeGenerator;

    protected AbstractSkinPainter() {
        this.shapeGenerator = new ShapeGenerator();
    }

    @Override
    protected final PaintContext getPaintContext() {
        return null;
    }

    /**
     * Creates a simple vertical gradient using the shape for bounds and the
     * colors for top and bottom colors.
     *
     * @param s      the shape to use for bounds.
     * @param colors the colors to use for the gradient.
     * @return the gradient.
     */
    protected final Paint createVerticalGradient(Shape s, Color[] colors) {
        Rectangle2D bounds = s.getBounds2D();
        float xCenter = (float) bounds.getCenterX();
        float yMin = (float) bounds.getMinY();
        float yMax = (float) bounds.getMaxY();
        return createGradient(xCenter, yMin, xCenter, yMax, new float[]{0f, 1f}, colors);
    }

    /**
     * Given parameters for creating a LinearGradientPaint, this method will
     * create and return a linear gradient paint. One primary purpose for this
     * method is to avoid creating a LinearGradientPaint where the start and end
     * points are equal. In such a case, the end y point is slightly increased
     * to avoid the overlap.
     *
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @param midpoints
     * @param colors
     * @return a valid LinearGradientPaint. This method never returns null.
     */
    private LinearGradientPaint createGradient(float x1, float y1, float x2, float y2, float[] midpoints, Color[] colors) {
        if (x1 == x2 && y1 == y2) {
            y2 += .00001f;
        }
        return new LinearGradientPaint(x1, y1, x2, y2, midpoints, colors);
    }

    protected final boolean testValid(int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && (w - x) > 0 && (h - y) > 0;
    }

    final Image getImage(String name) {
        String imagePath = IMAGES_PATH + name + ".png";
        try {
            return ImageIO.read(ClassLoader.getSystemResource(imagePath));
        } catch (Throwable e) {
            throw new RuntimeException("Error loading skin image: " + imagePath, e);
        }
    }
}
