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

import java.awt.Shape;
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
     * The style of a particular corner.
     */
    public enum CornerStyle {

        /** Make a square corner. */
        SQUARE,

        /** Make a rounded corner. */
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

        /** Round for a generic object's interior. */
        INTERIOR(baseRadius - 1),

        /** Round for a generic object's border. */
        BORDER(baseRadius),

        /** Round for a generic object's inner focus ring. */
        INNER_FOCUS(baseRadius + 1),

        /** Round for a generic object's outer focus ring. */
        OUTER_FOCUS(baseRadius + 2),

        /** Round for a slider thumb's interior. */
        SLIDER_INTERIOR(baseRadius - 2),

        /** Round for a slider thumb's border. */
        SLIDER_BORDER(baseRadius - 1),

        /** Round for a slider thumb's inner focus ring. */
        SLIDER_INNER_FOCUS(baseRadius),

        /** Round for a slider thumb's outer focus ring. */
        SLIDER_OUTER_FOCUS(baseRadius + 1),

        /** Round for a check box's interior. */
        CHECKBOX_INTERIOR(baseRadius / 2),

        /** Round for a check box's border. */
        CHECKBOX_BORDER((baseRadius + 1) / 2),

        /** Round for a check box's inner focus ring. */
        CHECKBOX_INNER_FOCUS((baseRadius + 2) / 2),

        /** Round for a check box's outer focus ring. */
        CHECKBOX_OUTER_FOCUS((baseRadius + 3) / 2),

        /** Round for a popup menu's border. */
        POPUP_BORDER(3),

        /** Round for a popup menu's interior. */
        POPUP_INTERIOR(2.5),

        /** Round for a frame's border. */
        FRAME_BORDER(baseRadius + 1),

        /** Round for a frame's inner highlight. */
        FRAME_INNER_HIGHLIGHT(baseRadius),

        /** Round for a frame's interior. */
        FRAME_INTERIOR(baseRadius - 1);

        /** The rounding radius. */
        private double radius;

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
         * @param  w the width of the rectangle.
         * @param  h the height of the rectangle.
         *
         * @return the radius (arc size) to use for rounding.
         */
        public double getRadius(int w, int h) {
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

    /** Used for generic shapes. */
    private final Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);

    /** Used for simple elliptical or circular shapes. */
    private final Ellipse2D ellipse = new Ellipse2D.Float();

    /**
     * Return a path for a rectangle with square corners.
     *
     * @param  x the X coordinate of the upper-left corner of the rectangle
     * @param  y the Y coordinate of the upper-left corner of the rectangle
     * @param  w the width of the rectangle
     * @param  h the height of the rectangle
     *
     * @return a path representing the shape.
     */
    public Shape createRectangle(final int x, final int y, final int w, final int h) {
        return createRoundRectangleInternal(x, y, w, h, 0, CornerStyle.SQUARE, CornerStyle.SQUARE, CornerStyle.SQUARE, CornerStyle.SQUARE);
    }

    /**
     * Return a path for a rectangle with rounded corners.
     *
     * @param  x    the X coordinate of the upper-left corner of the rectangle
     * @param  y    the Y coordinate of the upper-left corner of the rectangle
     * @param  w    the width of the rectangle
     * @param  h    the height of the rectangle
     * @param  size the CornerSize value representing the amount of rounding
     *
     * @return a path representing the shape.
     */
    public Shape createRoundRectangle(final int x, final int y, final int w, final int h, final CornerSize size) {
        return createRoundRectangle(x, y, w, h, size, CornerStyle.ROUNDED, CornerStyle.ROUNDED, CornerStyle.ROUNDED, CornerStyle.ROUNDED);
    }

    /**
     * Return a path for a rectangle with optionally rounded corners.
     *
     * @param  x           the X coordinate of the upper-left corner of the
     *                     rectangle
     * @param  y           the Y coordinate of the upper-left corner of the
     *                     rectangle
     * @param  w           the width of the rectangle
     * @param  h           the height of the rectangle
     * @param  size        the CornerSize value representing the amount of
     *                     rounding
     * @param  topLeft     the CornerStyle of the upper-left corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     * @param  bottomLeft  the CornerStyle of the lower-left corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     * @param  bottomRight the CornerStyle of the lower-right corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     * @param  topRight    the CornerStyle of the upper-right corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     *
     * @return a path representing the shape.
     */
    public Shape createRoundRectangle(final int x, final int y, final int w, final int h, final CornerSize size, final CornerStyle topLeft,
            final CornerStyle bottomLeft, final CornerStyle bottomRight, final CornerStyle topRight) {
        return createRoundRectangleInternal(x, y, w, h, size.getRadius(w, h), topLeft, bottomLeft, bottomRight, topRight);
    }

    /**
     * Return a path for a rectangle with square corners and no right side. This
     * is used for text fields that are part of a larger control, which is
     * placed to their left, e.g. spinners and editable combo boxes.
     *
     * <p>This path is suitable for drawing, not for filling.</p>
     *
     * @param  x the X coordinate of the upper-left corner of the rectangle
     * @param  y the Y coordinate of the upper-left corner of the rectangle
     * @param  w the width of the rectangle
     * @param  h the height of the rectangle
     *
     * @return a path representing the shape.
     */
    public Shape createOpenRectangle(final int x, final int y, final int w, final int h) {
        path.reset();
        path.moveTo(x + w, y);
        path.lineTo(x, y);
        path.lineTo(x, y + h);
        path.lineTo(x + w, y + h);

        return path;
    }

    /**
     * Return a path for a check mark.
     *
     * @param  x the X coordinate of the upper-left corner of the check mark
     * @param  y the Y coordinate of the upper-left corner of the check mark
     * @param  w the width of the check mark
     * @param  h the height of the check mark
     *
     * @return a path representing the shape.
     */
    public Shape createCheckMark(final int x, final int y, final int w, final int h) {
        double xf = w / 12.0;
        double hf = h / 12.0;

        path.reset();
        path.moveTo(x, y + 7.0 * hf);
        path.lineTo(x + 2.0 * xf, y + 7.0 * hf);
        path.lineTo(x + 4.75 * xf, y + 10.0 * hf);
        path.lineTo(x + 9.0 * xf, y);
        path.lineTo(x + 11.0 * xf, y);
        path.lineTo(x + 5.0 * xf, y + 12.0 * hf);
        path.closePath();

        return path;
    }

    /**
     * Return a path for an arrow pointing to the left.
     *
     * @param  x the X coordinate of the upper-left corner of the arrow
     * @param  y the Y coordinate of the upper-left corner of the arrow
     * @param  w the width of the arrow
     * @param  h the height of the arrow
     *
     * @return a path representing the shape.
     */
    public Shape createArrowLeft(final double x, final double y, final double w, final double h) {
        path.reset();
        path.moveTo(x + w, y);
        path.lineTo(x, y + h / 2.0);
        path.lineTo(x + w, y + h);
        path.closePath();

        return path;
    }

    /**
     * Return a path for an arrow pointing to the right.
     *
     * @param  x the X coordinate of the upper-left corner of the arrow
     * @param  y the Y coordinate of the upper-left corner of the arrow
     * @param  w the width of the arrow
     * @param  h the height of the arrow
     *
     * @return a path representing the shape.
     */
    public Shape createArrowRight(final double x, final double y, final double w, final double h) {
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x + w, y + h / 2);
        path.lineTo(x, y + h);
        path.closePath();

        return path;
    }

    /**
     * Return a path for an arrow pointing up.
     *
     * @param  x the X coordinate of the upper-left corner of the arrow
     * @param  y the Y coordinate of the upper-left corner of the arrow
     * @param  w the width of the arrow
     * @param  h the height of the arrow
     *
     * @return a path representing the shape.
     */
    public Shape createArrowUp(final double x, final double y, final double w, final double h) {
        path.reset();
        path.moveTo(x, y + h);
        path.lineTo(x + w / 2, y);
        path.lineTo(x + w, y + h);
        path.closePath();

        return path;
    }

    /**
     * Return a path for an arrow pointing down.
     *
     * @param  x the X coordinate of the upper-left corner of the arrow
     * @param  y the Y coordinate of the upper-left corner of the arrow
     * @param  w the width of the arrow
     * @param  h the height of the arrow
     *
     * @return a path representing the shape.
     */
    public Shape createArrowDown(final double x, final double y, final double w, final double h) {
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x + w / 2, y + h);
        path.lineTo(x + w, y);
        path.closePath();

        return path;
    }

    /**
     * Return a path for the patterned portion of an indeterminate progress bar.
     *
     * @param  x the X coordinate of the upper-left corner of the region
     * @param  y the Y coordinate of the upper-left corner of the region
     * @param  w the width of the region
     * @param  h the height of the region
     *
     * @return a path representing the shape.
     */
    public Shape createProgressBarIndeterminatePattern(int x, int y, int w, int h) {
      final double wHalf   = w / 2.0;
      final double xOffset = 5;
      path.reset();
      path.moveTo(xOffset, 0);
      path.lineTo(xOffset+wHalf, 0);
      path.curveTo(xOffset+wHalf-5, h/2-4, xOffset+wHalf+5, h/2+4, xOffset+wHalf, h);
      path.lineTo(xOffset, h);
      path.curveTo(xOffset+5, h/2+4, xOffset-5, h/2-4, xOffset, 0);
      path.closePath();

        return path;
    }

    /**
     * Return a path for a rounded internal drop shadow. This is used for
     * progress bar tracks and search fields.
     *
     * @param  x the X coordinate of the upper-left corner of the shadow
     * @param  y the Y coordinate of the upper-left corner of the shadow
     * @param  w the width of the shadow
     * @param  h the height of the rectangle
     *
     * @return a path representing the shadow.
     */
    public Shape createInternalDropShadowRounded(final int x, final int y, final int w, final int h) {
        final double radius = h / 2;
        final int    right  = x + w;
        final double bottom = y + radius;

        path.reset();

        // Upper edge.
        path.moveTo(x, bottom);
        path.quadTo(x, y, x + radius, y);
        path.lineTo(right - radius, y);
        path.quadTo(right, y, right, bottom);

        // Lower edge.
        path.lineTo(right - 1, bottom);
        path.quadTo(right - 2, y + 2, right - radius - 1, y + 2);
        path.lineTo(x + radius + 1, y + 2);
        path.quadTo(x + 2, y + 2, x + 1, bottom);

        path.closePath();

        return path;
    }

    /**
     * Return a path for a focus rectangle.
     *
     * <p>This path is suitable for filling.</p>
     *
     * @param  x the X coordinate of the upper-left corner of the rectangle
     * @param  y the Y coordinate of the upper-left corner of the rectangle
     * @param  w the width of the rectangle
     * @param  h the height of the rectangle
     *
     * @return a path representing the shape.
     */
    public Shape createFillableFocusRectangle(int x, int y, int w, int h) {
        final int left   = x;
        final int top    = y;
        final int right  = x + w;
        final int bottom = y + h;

        path.reset();
        path.moveTo(left, top);
        path.lineTo(left, bottom);
        path.lineTo(right, bottom);
        path.lineTo(right, top);

        final float offset = 1.4f;

        final float left2   = left + offset;
        final float top2    = top + offset;
        final float right2  = right - offset;
        final float bottom2 = bottom - offset;

        // TODO These two lines were curveTo in Nimbus. Perhaps we should
        // revisit this?
        path.lineTo(right2, top);
        path.lineTo(right2, bottom2);
        path.lineTo(left2, bottom2);
        path.lineTo(left2, top2);
        path.lineTo(right2, top2);
        path.lineTo(right2, top);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a simple bullet.
     *
     * @param  x        the X coordinate of the upper-left corner of the bullet
     * @param  y        the Y coordinate of the upper-left corner of the bullet
     * @param  diameter the diameter of the bullet
     *
     * @return a path representing the shape.
     */
    public Shape createBullet(int x, int y, int diameter) {
        return createEllipseInternal(x, y, diameter, diameter);
    }

    /**
     * Return a path for a radio button's concentric sections.
     *
     * @param  x        the X coordinate of the upper-left corner of the section
     * @param  y        the Y coordinate of the upper-left corner of the section
     * @param  diameter the diameter of the section
     *
     * @return a path representing the shape.
     */
    public Shape createRadioButton(int x, int y, int diameter) {
        return createEllipseInternal(x, y, diameter, diameter);
    }

    /**
     * Return a path for a continuous slider thumb's concentric sections.
     *
     * @param  x        the X coordinate of the upper-left corner of the section
     * @param  y        the Y coordinate of the upper-left corner of the section
     * @param  diameter the diameter of the section
     *
     * @return a path representing the shape.
     */
    public Shape createSliderThumbContinuous(final int x, final int y, final int diameter) {
        return createEllipseInternal(x, y, diameter, diameter);
    }

    /**
     * Return a path for a discrete slider thumb's concentric sections.
     *
     * @param  x    the X coordinate of the upper-left corner of the section
     * @param  y    the Y coordinate of the upper-left corner of the section
     * @param  w    the width of the section
     * @param  h    the height of the section
     * @param  size the CornerSize representing the rounding amount for the
     *              section
     *
     * @return a path representing the shape.
     */
    public Shape createSliderThumbDiscrete(final int x, final int y, final int w, final int h, final CornerSize size) {
        final double topArc     = size.getRadius(w, h);
        final double bottomArcH = size == CornerSize.INTERIOR ? 0 : 1;
        final double bottomArcW = 3;

        path.reset();
        path.moveTo(x, y + topArc);
        path.quadTo(x, y, x + topArc, y);
        path.lineTo(x + w - topArc, y);
        path.quadTo(x + w, y, x + w, y + topArc);
        path.lineTo(x + w, y + h / 2.0);
        path.quadTo(x + w - bottomArcW, y + h - bottomArcH, x + w / 2.0, y + h);
        path.quadTo(x + bottomArcW, y + h - bottomArcH, x, y + h / 2.0);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a "cancel" icon. This is a circle with a punched out
     * "x" in it.
     *
     * @param  x the X coordinate of the upper-left corner of the icon
     * @param  y the Y coordinate of the upper-left corner of the icon
     * @param  w the width of the icon
     * @param  h the height of the icon
     *
     * @return a path representing the shape.
     */
    public Shape createCancelIcon(int x, int y, int w, int h) {
        final double xMid = x + w / 2.0;
        final double yMid = y + h / 2.0;

        // Draw the circle.
        path.reset();
        path.moveTo(xMid, y);
        path.quadTo(x, y, x, yMid);
        path.quadTo(x, y + h, xMid, y + h);
        path.quadTo(x + w, y + h, x + w, yMid);
        path.quadTo(x + w, y, xMid, y);
        path.closePath();

        final double xOffsetL = w / 2.0 - 3;
        final double xOffsetS = w / 2.0 - 4;
        final double yOffsetL = h / 2.0 - 3;
        final double yOffsetS = h / 2.0 - 4;
        final double offsetC  = 1.5;

        // Erase the "x" with an inner subpath.
        path.moveTo(xMid, yMid - offsetC);
        path.lineTo(xMid + xOffsetS, yMid - yOffsetL);
        path.lineTo(yMid + xOffsetL, yMid - yOffsetS);
        path.lineTo(xMid + offsetC, yMid);
        path.lineTo(xMid + xOffsetL, yMid + yOffsetS);
        path.lineTo(xMid + xOffsetS, yMid + yOffsetL);
        path.lineTo(xMid, yMid + offsetC);
        path.lineTo(xMid - xOffsetS, yMid + yOffsetL);
        path.lineTo(xMid - xOffsetL, yMid + yOffsetS);
        path.lineTo(xMid - offsetC, yMid);
        path.lineTo(xMid - xOffsetL, yMid - yOffsetS);
        path.lineTo(xMid - xOffsetS, yMid - yOffsetL);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a "cancel" icon. This is a circle with a punched out
     * "x" in it.
     *
     * @param  x the X coordinate of the upper-left corner of the icon
     * @param  y the Y coordinate of the upper-left corner of the icon
     * @param  w the width of the icon
     * @param  h the height of the icon
     *
     * @return a path representing the shape.
     */
    public Shape createTabCloseIcon(int x, int y, int w, int h) {
        final double xMid = x + w / 2.0;
        final double yMid = y + h / 2.0;

        path.reset();

        final double xOffsetL = w / 2.0;
        final double xOffsetS = w / 2.0 - 1;
        final double yOffsetL = h / 2.0;
        final double yOffsetS = h / 2.0 - 1;
        final double offsetC  = 1;

        path.moveTo(xMid, yMid - offsetC);
        path.lineTo(xMid + xOffsetS, yMid - yOffsetL);
        path.lineTo(yMid + xOffsetL, yMid - yOffsetS);
        path.lineTo(xMid + offsetC, yMid);
        path.lineTo(xMid + xOffsetL, yMid + yOffsetS);
        path.lineTo(xMid + xOffsetS, yMid + yOffsetL);
        path.lineTo(xMid, yMid + offsetC);
        path.lineTo(xMid - xOffsetS, yMid + yOffsetL);
        path.lineTo(xMid - xOffsetL, yMid + yOffsetS);
        path.lineTo(xMid - offsetC, yMid);
        path.lineTo(xMid - xOffsetL, yMid - yOffsetS);
        path.lineTo(xMid - xOffsetS, yMid - yOffsetL);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a scroll bar cap. This is used when the buttons are
     * placed together at the opposite end of the scroll bar.
     *
     * @param  x the X coordinate of the upper-left corner of the cap
     * @param  y the Y coordinate of the upper-left corner of the cap
     * @param  w the width of the cap
     * @param  h the height of the cap
     *
     * @return a path representing the shape.
     */
    public Shape createScrollCap(int x, int y, int w, int h) {
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x, y + h);
        path.lineTo(x + w, y + h);
        addScrollGapPath(x, y, w, h, true);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a scroll bar button. This is used when the buttons are
     * placed apart at opposite ends of the scroll bar. This is a common shape
     * that is transformed to the appropriate button.
     *
     * @param  x the X coordinate of the upper-left corner of the button
     * @param  y the Y coordinate of the upper-left corner of the button
     * @param  w the width of the button
     * @param  h the height of the button
     *
     * @return a path representing the shape.
     */
    public Shape createScrollButtonApart(int x, int y, int w, int h) {
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x, y + h);
        path.lineTo(x + w, y + h);
        addScrollGapPath(x, y, w, h, true);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a scroll bar decrease button. This is used when the
     * buttons are placed together at one end of the scroll bar.
     *
     * @param  x the X coordinate of the upper-left corner of the button
     * @param  y the Y coordinate of the upper-left corner of the button
     * @param  w the width of the button
     * @param  h the height of the button
     *
     * @return a path representing the shape.
     */
    public Shape createScrollButtonTogetherDecrease(int x, int y, int w, int h) {
        path.reset();
        path.moveTo(x + w, y);
        path.lineTo(x + w, y + h);
        path.lineTo(x, y + h);
        addScrollGapPath(x, y, w, h, false);
        path.closePath();

        return path;
    }

    /**
     * Return a path for a scroll bar increase button. This is used when the
     * buttons are placed together at one end of the scroll bar.
     *
     * @param  x the X coordinate of the upper-left corner of the button
     * @param  y the Y coordinate of the upper-left corner of the button
     * @param  w the width of the button
     * @param  h the height of the button
     *
     * @return a path representing the shape.
     */
    public Shape createScrollButtonTogetherIncrease(int x, int y, int w, int h) {
        return createRectangle(x, y, w, h);
    }

    /**
     * Adds a hemispherical section to the current path. This is used to create
     * the gap in a scroll bar button or cap into which the scroll bar thumb
     * will fit.
     *
     * @param x        the X coordinate of the upper-left corner of the button
     *                 or cap
     * @param y        the Y coordinate of the upper-left corner of the button
     *                 or cap
     * @param w        the width of the button or cap
     * @param h        the height of the button or cap
     * @param isAtLeft {@code true} if the gap is at the left end of the button,
     *                 {@code false} if it is at the right.
     */
    private void addScrollGapPath(int x, int y, int w, int h, boolean isAtLeft) {
        final double hHalf    = h / 2.0;
        final double wFull    = isAtLeft ? w : 0;
        final double wHalfOff = isAtLeft ? w - hHalf : hHalf;

        path.quadTo(x + wHalfOff, y + h, x + wHalfOff, y + hHalf);
        path.quadTo(x + wHalfOff, y, x + wFull, y);
    }

    /**
     * Return a path for an ellipse.
     *
     * @param  x the X coordinate of the upper-left corner of the ellipse
     * @param  y the Y coordinate of the upper-left corner of the ellipse
     * @param  w the width of the ellipse
     * @param  h the height of the ellipse
     *
     * @return a path representing the shape.
     */
    private Shape createEllipseInternal(int x, int y, int w, int h) {
        ellipse.setFrame(x, y, w, h);

        return ellipse;
    }

    /**
     * Return a path for a rectangle with optionally rounded corners.
     *
     * @param  x           the X coordinate of the upper-left corner of the
     *                     rectangle
     * @param  y           the Y coordinate of the upper-left corner of the
     *                     rectangle
     * @param  w           the width of the rectangle
     * @param  h           the height of the rectangle
     * @param  radius      the radius (arc size) used for rounding
     * @param  topLeft     the CornerStyle of the upper-left corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     * @param  bottomLeft  the CornerStyle of the lower-left corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     * @param  bottomRight the CornerStyle of the lower-right corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     * @param  topRight    the CornerStyle of the upper-right corner. This must
     *                     be one of {@code CornerStyle.SQUARE} or
     *                     {@code CornerStyle.ROUNDED}.
     *
     * @return a path representing the shape.
     */
    private Shape createRoundRectangleInternal(final int x, final int y, final int w, final int h, final double radius,
            final CornerStyle topLeft, final CornerStyle bottomLeft, final CornerStyle bottomRight, final CornerStyle topRight) {
        // Convenience variables.
        final int left   = x;
        final int top    = y;
        final int right  = x + w;
        final int bottom = y + h;

        // Start the path.
        path.reset();

        // Move to top left and draw rounded corner if requested.
        switch (topLeft) {

        case SQUARE:
            path.moveTo(left, top);
            break;

        case ROUNDED:
            path.moveTo(left + radius, top);
            path.quadTo(left, top, left, top + radius);
            break;
        }

        // Draw through bottom left corner.
        switch (bottomLeft) {

        case SQUARE:
            path.lineTo(left, bottom);
            break;

        case ROUNDED:
            path.lineTo(left, bottom - radius);
            path.quadTo(left, bottom, left + radius, bottom);
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
            path.lineTo(right, top);
            break;

        case ROUNDED:
            path.lineTo(right, top + radius);
            path.quadTo(right, top, right - radius, top);
            break;
        }

        // Close the path.
        path.closePath();

        return path;
    }
}
