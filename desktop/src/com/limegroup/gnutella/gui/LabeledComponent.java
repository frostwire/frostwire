package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;

/**
 * This class creates a standardized <tt>JPanel</tt> that includes a
 * <tt>Component</tt> with a <tt>JLabel</tt> next to it.<p>
 * <p>
 * The label can be placed to the left, to the right, on top, or on bottom
 * of the <tt>Component</tt> depending on the parameters used in the
 * constructor.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class LabeledComponent {
    /**
     * Constant alignment key for aligning the label to the left of the
     * <tt>Component</tt>.
     */
    public static final int LEFT = 10;
    /**
     * Constant alignment key for aligning the label to the right of the
     * <tt>Component</tt>.
     */
    private static final int RIGHT = 11;
    /**
     * This will create a "glue" at the left of the panel, pushing the
     * label/component pair to the right.
     */
    public static final int LEFT_GLUE = 120;
    /**
     * This will give the panel no glue.
     */
    public static final int NO_GLUE = 150;
    /**
     * Constant alignment key for aligning the label on the top of the
     * <tt>Component</tt> justified in the center.
     */
    private static final int TOP_CENTER = 12;
    /**
     * Constant alignment key for aligning the label on the top of the
     * <tt>Component</tt> with left justification.
     */
    private static final int TOP_LEFT = 13;
    /**
     * Constant alignment key for aligning the label on the top of the
     * <tt>Component</tt> with right justification.
     */
    private static final int TOP_RIGHT = 14;
    /**
     * Constant alignment key for aligning the label onthe bottom of the
     * <tt>Component</tt> justified in the center.
     */
    private static final int BOTTOM_CENTER = 15;
    /**
     * Constant alignment key for aligning the label onthe bottom of the
     * <tt>Component</tt> with left justification.
     */
    private static final int BOTTOM_LEFT = 16;
    /**
     * Constant alignment key for aligning the label on the bottom of the
     * <tt>Component</tt> with right justification.
     */
    private static final int BOTTOM_RIGHT = 17;
    /**
     * This will create a "glue" at the top of the panel, pushing the
     * label/component pair to the bottom.
     */
    private static final int TOP_GLUE = 100;
    /**
     * This will create a "glue" at the bottom of the panel, pushing the
     * label/component pair to the top.
     */
    private static final int BOTTOM_GLUE = 110;
    /**
     * This will create a "glue" at the right of the panel, pushing the
     * label/component pair to the right.
     */
    private static final int RIGHT_GLUE = 130;
    /**
     * This will give the panel glue at the right and left or top and bottom of
     * the panel, pushing the label/component pair in the middle.
     */
    private static final int SURROUND_GLUE = 140;
    /**
     * Constant for the <tt>JPanel</tt> containing the label and field.
     */
    private final BoxPanel PANEL = new BoxPanel(BoxPanel.X_AXIS);

    /**
     * Constructs a <tt>JPanel</tt> with a label next to a field with
     * standard spacing between them.<p>
     * <p>
     * This constructor places the label to the left of the
     * <tt>Component</tt>.
     *
     * @param key  the key for the text for the locale-specific label
     * @param comp the component to put the label next to
     */
    public LabeledComponent(final String key, final Component comp) {
        this(key, comp, NO_GLUE, LEFT);
    }

    /**
     * Constructs a <tt>JPanel</tt> with a label next to a field with
     * standard spacing between them.<p>
     * <p>
     * This constructor places the label to the left of the
     * <tt>Component</tt>.
     *
     * @param key  the key for the text for the locale-specific label
     * @param comp the component to put the label next to
     * @param glue specifies the type of glue to add to the panel
     */
    public LabeledComponent(final String key, final Component comp,
                            int glue) {
        this(key, comp, glue, LEFT);
    }

    /**
     * Constructs a <tt>JPanel</tt> with a label next to a field with
     * standard spacing between them.<p>
     * <p>
     * This method allows for a great deal of customizability for the
     * layout of the given component, such as different alignments of
     * the label in relation to the <tt>Component</tt> as well as a
     * glue on any of the four sides that forces the label/component
     * pair to one side of the panel.
     *
     * @param key       the key for the locale-specific label
     * @param comp      the component to put the label next to
     * @param alignment specifies the placement of the label in relation
     *                  to the <tt>Component</tt>
     * @param glue      specifies the type of glue to add to the panel
     */
    public LabeledComponent(final String key, final Component comp,
                            final int glue, final int alignment) {
        assert (alignment == LEFT || alignment == RIGHT
                || alignment == TOP_LEFT || alignment == TOP_CENTER
                || alignment == TOP_RIGHT || alignment == BOTTOM_LEFT
                || alignment == BOTTOM_CENTER || alignment == BOTTOM_RIGHT);
        assert (glue == LEFT_GLUE || glue == RIGHT_GLUE
                || glue == SURROUND_GLUE || glue == NO_GLUE);
        String text = I18n.tr(key);
        JLabel label = new JLabel(text);
        if (alignment == LEFT || alignment == RIGHT) {
            if (glue == LEFT_GLUE || glue == SURROUND_GLUE) {
                PANEL.add(Box.createHorizontalGlue());
            }
        }
        if (alignment == TOP_LEFT || alignment == TOP_CENTER ||
                alignment == TOP_RIGHT || alignment == BOTTOM_LEFT ||
                alignment == BOTTOM_CENTER || alignment == BOTTOM_RIGHT) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            if (glue == TOP_GLUE || glue == SURROUND_GLUE) {
                PANEL.add(Box.createVerticalGlue());
            }
        }
        if (alignment == LEFT) {
            PANEL.add(label);
            PANEL.add(Box.createRigidArea(new Dimension(6, 0)));
            PANEL.add(comp);
        } else if (alignment == RIGHT) {
            PANEL.add(comp);
            PANEL.add(Box.createRigidArea(new Dimension(6, 0)));
            PANEL.add(label);
        } else if (alignment == TOP_LEFT) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            BoxPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(label);
            labelPanel.add(Box.createHorizontalGlue());
            PANEL.add(labelPanel);
            PANEL.add(Box.createRigidArea(new Dimension(0, 6)));
            PANEL.add(comp);
        } else if (alignment == TOP_CENTER) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            BoxPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(Box.createHorizontalGlue());
            labelPanel.add(label);
            labelPanel.add(Box.createHorizontalGlue());
            PANEL.add(labelPanel);
            PANEL.add(Box.createRigidArea(new Dimension(0, 6)));
            PANEL.add(comp);
        } else if (alignment == TOP_RIGHT) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            BoxPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(Box.createHorizontalGlue());
            labelPanel.add(label);
            PANEL.add(labelPanel);
            PANEL.add(Box.createRigidArea(new Dimension(0, 6)));
            PANEL.add(comp);
        } else if (alignment == BOTTOM_LEFT) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            PANEL.add(comp);
            PANEL.add(Box.createRigidArea(new Dimension(0, 6)));
            BoxPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(label);
            labelPanel.add(Box.createHorizontalGlue());
            PANEL.add(labelPanel);
        } else if (alignment == BOTTOM_CENTER) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            PANEL.add(comp);
            PANEL.add(Box.createRigidArea(new Dimension(0, 6)));
            BoxPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(Box.createHorizontalGlue());
            labelPanel.add(label);
            labelPanel.add(Box.createHorizontalGlue());
            PANEL.add(labelPanel);
        } else if (alignment == BOTTOM_RIGHT) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            PANEL.add(comp);
            PANEL.add(Box.createRigidArea(new Dimension(0, 6)));
            BoxPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(Box.createHorizontalGlue());
            labelPanel.add(label);
            PANEL.add(labelPanel);
        } else {
            String msg = "The specified alignment is invalid.";
            throw new IllegalArgumentException(msg);
        }
        if (alignment == LEFT || alignment == RIGHT) {
            if (glue == RIGHT_GLUE || glue == SURROUND_GLUE) {
                PANEL.add(Box.createHorizontalGlue());
            }
        }
        if (alignment == TOP_LEFT || alignment == TOP_CENTER ||
                alignment == TOP_RIGHT || alignment == BOTTOM_LEFT ||
                alignment == BOTTOM_CENTER || alignment == BOTTOM_RIGHT) {
            PANEL.setOrientation(BoxPanel.Y_AXIS);
            if (glue == BOTTOM_GLUE || glue == SURROUND_GLUE) {
                PANEL.add(Box.createVerticalGlue());
            }
        }
    }

    /**
     * Returns the <tt>Component</tt> that contains the <tt>JLabel</tt>
     * and its associated <tt>Component</tt>.
     *
     * @return the <tt>Component</tt> that contains the <tt>JLabel</tt>
     * and the <tt>Component</tt> next to it
     */
    public JComponent getComponent() {
        return PANEL;
    }
}
