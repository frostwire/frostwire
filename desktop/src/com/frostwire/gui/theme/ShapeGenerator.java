/*
 * Copyright (c) 2009 Kathryn Huxtable and Kenneth Orr.
 *
 * This file is part of the SeaGlass Pluggable Look and Feel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id: ShapeGenerator.java 1595 2011-08-09 20:33:48Z rosstauscher@gmx.de $
 */
package com.frostwire.gui.theme;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Return various shapes used by the Painter classes.
 *
 * @author Kathryn Huxtable
 */
public final class ShapeGenerator {
    /**
     * The base radius (arc size) for most control's borders. This is used to
     * calculate the rest of the arc sizes in a relative manner.
     */
    private static final double baseRadius = 4d;
    /**
     * Used for generic shapes.
     */
    private final Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
    /**
     * Used for simple elliptical or circular shapes.
     */
    private final Ellipse2D ellipse = new Ellipse2D.Float();

    /**
     * Return a path for a rectangle with square corners.
     *
     * @param x the X coordinate of the upper-left corner of the rectangle
     * @param y the Y coordinate of the upper-left corner of the rectangle
     * @param w the width of the rectangle
     * @param h the height of the rectangle
     * @return a path representing the shape.
     */
    public Shape createRectangle(final int x, final int y, final int w, final int h) {
        return createRoundRectangleInternal(x, y, w, h, 0, CornerStyle.SQUARE, CornerStyle.SQUARE, CornerStyle.SQUARE, CornerStyle.SQUARE);
    }

    /**
     * Return a path for a rectangle with rounded corners.
     *
     * @param x    the X coordinate of the upper-left corner of the rectangle
     * @param y    the Y coordinate of the upper-left corner of the rectangle
     * @param w    the width of the rectangle
     * @param h    the height of the rectangle
     * @param size the CornerSize value representing the amount of rounding
     * @return a path representing the shape.
     */
    Shape createRoundRectangle(final int x, final int y, final int w, final int h, final CornerSize size) {
        return createRoundRectangle(x, y, w, h, size, CornerStyle.ROUNDED, CornerStyle.ROUNDED, CornerStyle.ROUNDED, CornerStyle.ROUNDED);
    }

    /**
     * Return a path for a rectangle with optionally rounded corners.
     *
     * @param x           the X coordinate of the upper-left corner of the
     *                    rectangle
     * @param y           the Y coordinate of the upper-left corner of the
     *                    rectangle
     * @param w           the width of the rectangle
     * @param h           the height of the rectangle
     * @param size        the CornerSize value representing the amount of
     *                    rounding
     * @param topLeft     the CornerStyle of the upper-left corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @param bottomLeft  the CornerStyle of the lower-left corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @param bottomRight the CornerStyle of the lower-right corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @param topRight    the CornerStyle of the upper-right corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @return a path representing the shape.
     */
    private Shape createRoundRectangle(final int x, final int y, final int w, final int h, final CornerSize size, final CornerStyle topLeft,
                                       final CornerStyle bottomLeft, final CornerStyle bottomRight, final CornerStyle topRight) {
        return createRoundRectangleInternal(x, y, w, h, size.getRadius(w, h), topLeft, bottomLeft, bottomRight, topRight);
    }

    /**
     * Return a path for an arrow pointing to the left.
     *
     * @param x the X coordinate of the upper-left corner of the arrow
     * @param y the Y coordinate of the upper-left corner of the arrow
     * @param w the width of the arrow
     * @param h the height of the arrow
     * @return a path representing the shape.
     */
    Shape createArrowLeft(final double x, final double y, final double w, final double h) {
        path.reset();
        path.moveTo(x + w, y);
        path.lineTo(x, y + h / 2.0);
        path.lineTo(x + w, y + h);
        path.closePath();
        return path;
    }

    /**
     * Return a path for the patterned portion of an indeterminate progress bar.
     *
     * @param x the X coordinate of the upper-left corner of the region
     * @param y the Y coordinate of the upper-left corner of the region
     * @param w the width of the region
     * @param h the height of the region
     * @return a path representing the shape.
     */
    Shape createProgressBarIndeterminatePattern(int x, int y, int w, int h) {
        final double wHalf = w / 2.0;
        final double xOffset = 5;
        path.reset();
        path.moveTo(xOffset, 0);
        path.lineTo(xOffset + wHalf, 0);
        path.curveTo(xOffset + wHalf - 5, h / 2 - 4, xOffset + wHalf + 5, h / 2 + 4, xOffset + wHalf, h);
        path.lineTo(xOffset, h);
        path.curveTo(xOffset + 5, h / 2 + 4, xOffset - 5, h / 2 - 4, xOffset, 0);
        path.closePath();
        return path;
    }

    /**
     * Return a path for a radio button's concentric sections.
     *
     * @param x        the X coordinate of the upper-left corner of the section
     * @param y        the Y coordinate of the upper-left corner of the section
     * @param diameter the diameter of the section
     * @return a path representing the shape.
     */
    public Shape createRadioButton(int x, int y, int diameter) {
        return createEllipseInternal(x, y, diameter, diameter);
    }

    /**
     * Return a path for an ellipse.
     *
     * @param x the X coordinate of the upper-left corner of the ellipse
     * @param y the Y coordinate of the upper-left corner of the ellipse
     * @param w the width of the ellipse
     * @param h the height of the ellipse
     * @return a path representing the shape.
     */
    private Shape createEllipseInternal(int x, int y, int w, int h) {
        ellipse.setFrame(x, y, w, h);
        return ellipse;
    }

    /**
     * Return a path for a rectangle with optionally rounded corners.
     *
     * @param x           the X coordinate of the upper-left corner of the
     *                    rectangle
     * @param y           the Y coordinate of the upper-left corner of the
     *                    rectangle
     * @param w           the width of the rectangle
     * @param h           the height of the rectangle
     * @param radius      the radius (arc size) used for rounding
     * @param topLeft     the CornerStyle of the upper-left corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @param bottomLeft  the CornerStyle of the lower-left corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @param bottomRight the CornerStyle of the lower-right corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @param topRight    the CornerStyle of the upper-right corner. This must
     *                    be one of {@code CornerStyle.SQUARE} or
     *                    {@code CornerStyle.ROUNDED}.
     * @return a path representing the shape.
     */
    private Shape createRoundRectangleInternal(final int x, final int y, final int w, final int h, final double radius,
                                               final CornerStyle topLeft, final CornerStyle bottomLeft, final CornerStyle bottomRight, final CornerStyle topRight) {
        // Convenience variables.
        final int right = x + w;
        final int bottom = y + h;
        // Start the path.
        path.reset();
        // Move to top left and draw rounded corner if requested.
        switch (topLeft) {
            case SQUARE:
                path.moveTo(x, y);
                break;
            case ROUNDED:
                path.moveTo(x + radius, y);
                path.quadTo(x, y, x, y + radius);
                break;
        }
        // Draw through bottom left corner.
        switch (bottomLeft) {
            case SQUARE:
                path.lineTo(x, bottom);
                break;
            case ROUNDED:
                path.lineTo(x, bottom - radius);
                path.quadTo(x, bottom, x + radius, bottom);
                break;
        }
        // Draw through bottom right corner.
        switch (bottomRight) {
            case SQUARE:
                path.lineTo(right, bottom);
                break;
            case ROUNDED:
                path.lineTo(right - radius, bottom);
                path.quadTo(right, bottom, right, bottom - radius);
        }
        // Draw through top right corner.
        switch (topRight) {
            case SQUARE:
                path.lineTo(right, y);
                break;
            case ROUNDED:
                path.lineTo(right, y + radius);
                path.quadTo(right, y, right - radius, y);
                break;
        }
        // Close the path.
        path.closePath();
        return path;
    }

    /**
     * The style of a particular corner.
     */
    public enum CornerStyle {
        /**
         * Make a square corner.
         */
        SQUARE,
        /**
         * Make a rounded corner.
         */
        ROUNDED,
    }

    /**
     * The rounding amount for a corner.
     */
    public enum CornerSize {
        /**
         * Round using half the height, producing a nice quarter of a circle for
         * the entire quarter of the rectangle. Use for horizontal lozenges that
         * are filled.
         */
        ROUND_HEIGHT(0),
        /**
         * Round using half the height plus 1, producing a nice quarter of a
         * circle for the entire quarter of the rectangle. Use for horizontal
         * lozenges that are drawn.
         */
        ROUND_HEIGHT_DRAW(0),
        /**
         * Round using half the width, producing a nice quarter of a circle for
         * the entire quarter of the rectangle. Use for vertical lozenges that
         * are filled.
         */
        ROUND_WIDTH(0),
        /**
         * Round using half the width plus 1, producing a nice quarter of a
         * circle for the entire quarter of the rectangle. Use for vertical
         * lozenges that are drawn.
         */
        ROUND_WIDTH_DRAW(0),
        /**
         * Round for a generic object's interior.
         */
        INTERIOR(baseRadius - 1),
        /**
         * Round for a generic object's border.
         */
        BORDER(baseRadius),
        /**
         * Round for a generic object's inner focus ring.
         */
        INNER_FOCUS(baseRadius + 1),
        /**
         * Round for a generic object's outer focus ring.
         */
        OUTER_FOCUS(baseRadius + 2),
        /**
         * Round for a slider thumb's interior.
         */
        SLIDER_INTERIOR(baseRadius - 2),
        /**
         * Round for a slider thumb's border.
         */
        SLIDER_BORDER(baseRadius - 1),
        /**
         * Round for a slider thumb's inner focus ring.
         */
        SLIDER_INNER_FOCUS(baseRadius),
        /**
         * Round for a slider thumb's outer focus ring.
         */
        SLIDER_OUTER_FOCUS(baseRadius + 1),
        /**
         * Round for a check box's interior.
         */
        CHECKBOX_INTERIOR(baseRadius / 2),
        /**
         * Round for a check box's border.
         */
        CHECKBOX_BORDER((baseRadius + 1) / 2),
        /**
         * Round for a check box's inner focus ring.
         */
        CHECKBOX_INNER_FOCUS((baseRadius + 2) / 2),
        /**
         * Round for a check box's outer focus ring.
         */
        CHECKBOX_OUTER_FOCUS((baseRadius + 3) / 2),
        /**
         * Round for a popup menu's border.
         */
        POPUP_BORDER(3),
        /**
         * Round for a popup menu's interior.
         */
        POPUP_INTERIOR(2.5),
        /**
         * Round for a frame's border.
         */
        FRAME_BORDER(baseRadius + 1),
        /**
         * Round for a frame's inner highlight.
         */
        FRAME_INNER_HIGHLIGHT(baseRadius),
        /**
         * Round for a frame's interior.
         */
        FRAME_INTERIOR(baseRadius - 1);
        /**
         * The rounding radius.
         */
        private final double radius;

        /**
         * Create the corner size.
         *
         * @param radius the radius for rounding.
         */
        CornerSize(double radius) {
            this.radius = radius;
        }

        /**
         * Return the rounding radius. Note that the {@code ROUND*} values are
         * handled specially, as the rounding is dependent on the size of the
         * rectangle.
         *
         * @param w the width of the rectangle.
         * @param h the height of the rectangle.
         * @return the radius (arc size) to use for rounding.
         */
        double getRadius(int w, int h) {
            switch (this) {
                case ROUND_HEIGHT:
                    return h / 2.0;
                case ROUND_HEIGHT_DRAW:
                    return (h + 1) / 2.0;
                case ROUND_WIDTH:
                    return w / 2.0;
                case ROUND_WIDTH_DRAW:
                    return (w + 1) / 2.0;
                default:
                    return radius;
            }
        }
    }
}
