package com.limegroup.gnutella.gui.search;

import java.awt.*;

/**
 * Optimized class to draw vertical fades from one color to another.
 */
public final class Ditherer {
    /**
     * Specifies that the gradient is drawn along horizontal axis.
     */
    private static final int X_AXIS = 0;
    /**
     * Specifies that the gradient is drawn along the vertical axis.
     */
    public static final int Y_AXIS = 1;
    private final int _redT;
    private final int _greenT;
    private final int _blueT;
    private final int _redB;
    private final int _greenB;
    private final int _blueB;
    private int STEPS;
    private boolean fixedSteps;
    private Shader shader;
    private int orientation;

    /**
     * Constructs a ditherer.
     *
     * @param from        the color on the left or on the top depending on
     *                    <code>orientation</code>
     * @param to          the color on the right or on the bottom depending on
     *                    <code>orientation</code>
     * @param orientation the axis along which the gradient is drawn.
     * @param shader      the {@link Shader} used for determining the gradient
     *                    between the two colors
     */
    public Ditherer(Color from, Color to, int orientation, Shader shader) {
        this(from, to, orientation, shader, 0);
    }

    private Ditherer(Color from, Color to, int orientation, Shader shader, int steps) {
        if (orientation != X_AXIS && orientation != Y_AXIS) {
            throw new IllegalArgumentException("not a valid orientation");
        }
        if (steps > 0) {
            STEPS = steps;
            fixedSteps = true;
        }
        _redT = from.getRed();
        _greenT = from.getGreen();
        _blueT = from.getBlue();
        _redB = to.getRed();
        _greenB = to.getGreen();
        _blueB = to.getBlue();
        this.shader = shader;
        this.orientation = orientation;
    }

    private static int round(float color) {
        int ret = Math.round(color);
        if (ret < 0)
            return 0;
        return Math.min(ret, 255);
    }

    /**
     * Draws the requested fade to g, with the given width and height.
     */
    public void draw(final Graphics g, final int height, final int width) {
        int constantDim;
        int changingDim;
        if (orientation == X_AXIS) {
            changingDim = width;
            constantDim = height;
        } else {
            changingDim = height;
            constantDim = width;
        }
        int dimStep;
        if (fixedSteps) {
            dimStep = changingDim / STEPS;
        } else {
            dimStep = 2;
            STEPS = changingDim / dimStep;
        }
        float red = _redT;
        float green = _greenT;
        float blue = _blueT;
        int offset = 0;
        //Draw a rectangle for each step
        for (int i = 0; i < STEPS; i++) {
            Color c = new Color(round(red), round(green), round(blue));
            g.setColor(c);
            drawRect(g, offset, constantDim, dimStep);
            offset += dimStep;
            float value = shader.getValue((float) i / (float) STEPS);
            red = _redT + (_redB - _redT) * value;
            green = _greenT + (_greenB - _greenT) * value;
            blue = _blueT + (_blueB - _blueT) * value;
        }
        //Ensure bottom is filled.
        Color c = new Color(round(red), round(green), round(blue));
        g.setColor(c);
        drawRect(g, offset, constantDim, changingDim - offset);
    }

    private void drawRect(final Graphics g, int offset, int constantDim, int changingDim) {
        if (orientation == Y_AXIS) {
            g.fillRect(0, offset, constantDim, changingDim);
        } else {
            g.fillRect(offset, 0, changingDim, constantDim);
        }
    }

    /**
     * Defines how the gradient between the two colors should be drawn.
     */
    interface Shader {
        /**
         * Returns a value between 0 and 1 for a value between 0 and 1.
         */
        float getValue(float value);
    }

    /**
     * Provides a polygonal gradient between the two colors.
     */
    public static class PolygonShader implements Shader {
        private final float exponent;

        /**
         * Constructs a polygon shader for an exponent.
         */
        public PolygonShader(float exponent) {
            this.exponent = exponent;
        }

        /**
         * Returns <code>value^exponent</code>.
         */
        public float getValue(float value) {
            return (float) Math.pow(value, exponent);
        }
    }
}
