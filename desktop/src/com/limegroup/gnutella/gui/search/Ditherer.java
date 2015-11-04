package com.limegroup.gnutella.gui.search;

import java.awt.Color;
import java.awt.Graphics;


/**
 * Optimized class to draw vertical fades from one color to another.
 */
public final class Ditherer {
	
    private final int _redT;
    private final int _greenT;
    private final int _blueT;

    private final int _redB;
    private final int _greenB;
    private final int _blueB;

    private  int STEPS;
    private boolean fixedSteps;
    
    private final Color from;
    private final Color to;
    private Shader shader;
    private int orientation;
    
    /**
     * Specifies that the gradient is drawn along horizontal axis.
     */
    public static final int X_AXIS = 0;
    /**
     * Specifies that the gradient is drawn along the vertical axis.
     */
    public static final int Y_AXIS = 1;

    /** 
     * Constructs a new ditherer that will fade from top to bottom vertically.
     * @throws IllegalArgumentException when <code>steps</code> is less than or
     * equal to 0
     * @throws IllegalArgumentException if the orientation is neither {@link X_AXIS}
     * nor {@link Y_AXIS} 
     */

    public Ditherer(int steps, Color from, Color to) {
        this(from, to, Y_AXIS, new LinearShader(), steps);
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be greater than zero");
        }
    }
    
    /**
     * Constructs a ditherer.
     * @param from the color on the left or on the top depending on 
     * <code>orientation</code>
     * @param to the color on the right or on the bottom depending on 
     * <code>orientation</code>
     * @param orientation the axis along which the gradient is drawn.
     * @param shader the {@link Shader} used for determining the gradient 
     * between the two colors
     * @throws IllegalArgumentException if the orientation is neither {@link X_AXIS}
     * nor {@link Y_AXIS} 
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

        this.from = from;
        this.to = to;
        this.shader = shader;
        this.orientation = orientation;
	}
	
    /**
     * Returns the starting color for the gradient.
     */
	public Color getFromColor() { return from; }

	/**
	 * Returns the ending color for the gradient.
	 */
	public Color getToColor() { return to; }

    /** 
     * Draws the requested fade to g, with the given width and height.
     */
    public void draw(final Graphics g, final int height, final int width) {
    	int constantDim;
    	int changingDim;
    	if (orientation == X_AXIS) {
    		changingDim = width;
    		constantDim = height;
    	}
    	else {
    		changingDim = height;
    		constantDim = width;
    	}
    	
    	int dimStep;
    	if (fixedSteps) {
    		dimStep = changingDim / STEPS;
    	}
    	else {
    		dimStep = 2;
    		STEPS = changingDim/ dimStep;
    	}
    	
        float red=_redT;
        float green=_greenT;
        float blue=_blueT;

        int offset = 0;

        //Draw a rectangle for each step
        for (int i = 0; i < STEPS; i++) {
            Color c = new Color(round(red), round(green), round(blue));
            g.setColor(c);
            drawRect(g, offset, constantDim, dimStep);
            offset += dimStep;
            float value = shader.getValue((float)i / (float)STEPS);
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
    	}
    	else {
    		g.fillRect(offset, 0, changingDim, constantDim);
    	}
    }
    
    private static int round(float color) {
        int ret=Math.round(color);
        if (ret<0)
            return 0;
        if (ret>255)
            return 255;
        else
            return ret;
    }

    /**
     * Defines how the gradient between the two colors should be drawn.
     */
    public interface Shader {
    	/**
    	 * Returns a value between 0 and 1 for a value between 0 and 1.
    	 * @return
    	 */
    	float getValue(float value);
    }

    /**
     * Provides a linear gradient between the two colors of the ditherer. 
     */
    public static class LinearShader implements Shader {

		public float getValue(float value) {
			return value;
		}
    }
    
    /**
     * Provides a polygonal gradient between the two colors.
     */
    public static class PolygonShader implements Shader {

    	private float exponent;

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
			return (float)Math.pow(value, exponent);
		}
    }
}
