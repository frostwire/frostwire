package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.actions.LimeAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.util.EventListener;

/**
 * This class generates a row of buttons with a standard spacing
 * between them. The row of buttons can be oriented either
 * horizontally or vertically, depending on the parameter.
 */
public final class ButtonRow extends JPanel {
    /**
     * The number of pixels separating buttons.
     */
    public static final int BUTTON_SEP = 6;
    /**
     * Specifies that the buttons should be aligned along the x axis.
     */
    public static final int X_AXIS = BoxLayout.X_AXIS;
    /**
     * Specifies that the buttons should be aligned along the y axis.
     */
    private static final int Y_AXIS = BoxLayout.Y_AXIS;
    /**
     * This will create a "glue" at the top of the button panel, pushing
     * the buttons to the bottom.
     */
    private static final int TOP_GLUE = 10;
    /**
     * This will create a "glue" at the bottom of the button panel, pushing
     * the buttons to the top.
     */
    private static final int BOTTOM_GLUE = 11;
    /**
     * This will create a "glue" at the left of the button panel, pushing
     * the buttons to the left.
     */
    public static final int LEFT_GLUE = 12;
    /**
     * This will create a "glue" at the right of the button panel, pushing
     * the buttons to the right.
     */
    public static final int RIGHT_GLUE = 13;
    /**
     * This will give the button panel no glue, leaving the buttons in
     * the middle.
     */
    public static final int NO_GLUE = 14;
    /**
     *
     */
    private static final long serialVersionUID = 2171794172705055068L;
    /**
     * The array of <tt>JButton</tt>s.
     */
    private JButton[] _buttons;

    public ButtonRow(String[] labelKeys,
                     String[] toolTipKeys,
                     EventListener[] listeners,
                     String[] iconNames) {
        this(labelKeys, toolTipKeys, listeners, iconNames, X_AXIS, NO_GLUE);
    }

    /**
     * Creates a row of buttons with standard separation between
     * each button, aligned either vertically or horizontally,
     * with or without glue.  The lengths of all arrays must be equal,
     * or this will throw <tt>IllegalArgumentException</tt>.
     *
     * @param labelKeys   the array of keys for looking up the locale-specific
     *                    labels to use for the buttons
     * @param listeners   the array of <tt>ActionListeners</tt> to use
     *                    for the buttons
     * @param orientation the orientation to use for the row of buttons,
     *                    either ButtonRow.X_AXIS or ButtonRow.Y_AXIS
     * @param glue        the glue determining the placement of the buttons,
     *                    either TOP_GLUE, BOTTOM_GLUE, LEFT_GLUE, RIGHT_GLUE,
     *                    or NO_GLUE
     */
    public ButtonRow(String[] labelKeys,
                     String[] toolTipKeys,
                     EventListener[] listeners,
                     int orientation,
                     int glue) {
        this(labelKeys, toolTipKeys, listeners, null, orientation, glue);
    }

    private ButtonRow(String[] labelKeys,
                      String[] toolTipKeys,
                      EventListener[] listeners,
                      String[] iconNames,
                      int orientation,
                      int glue) {
        if ((labelKeys.length != listeners.length) ||
                (toolTipKeys.length != listeners.length)) {
            throw new IllegalArgumentException("invalid ButtonRow constructor: " +
                    "array lengths must be equal");
        }
        BoxLayout bl = new BoxLayout(this, orientation);
        setLayout(bl);
        final int length = labelKeys.length;
        final int sepLength = length - 1;
        _buttons = new JButton[length];
        Component[] separators = new Component[sepLength];
        int i = 0;
        while (i < length) {
            String label = "";
            if (!"".equals(labelKeys[i]))
                label = I18n.tr(labelKeys[i]);
            if (iconNames != null && iconNames[i] != null)
                _buttons[i] = new IconButton(label, iconNames[i]);
            else
                _buttons[i] = new JButton(label);
            if (toolTipKeys[i] != null) {
                String tip = I18n.tr(toolTipKeys[i]);
                _buttons[i].setToolTipText(tip);
            }
            i++;
        }
        setListeners(listeners);
        i = 0;
        Dimension d;
        if (orientation == BoxLayout.X_AXIS) {
            d = new Dimension(BUTTON_SEP, 0);
            while (i < (sepLength)) {
                separators[i] = Box.createRigidArea(d);
                i++;
            }
        }
        // otherwise the orientation should be BoxLayout.Y_AXIS
        else {
            d = new Dimension(0, BUTTON_SEP);
            while (i < (sepLength)) {
                separators[i] = Box.createRigidArea(d);
                i++;
            }
        }
        i = 0;
        if (glue == TOP_GLUE && orientation == Y_AXIS)
            add(Box.createVerticalGlue());
        else if (glue == LEFT_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        else if (glue == NO_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        while (i < length) {
            add(_buttons[i]);
            if (i < sepLength) {
                add(separators[i]);
            }
            i++;
        }
        if (glue == BOTTOM_GLUE && orientation == Y_AXIS)
            add(Box.createVerticalGlue());
        else if (glue == RIGHT_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        else if (glue == NO_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
    }

    /**
     * Creates a row of buttons for an array of actions.
     *
     * @param actions     the actions which will be shown in the button row
     * @param orientation the orientation to use for the row of buttons,
     *                    either ButtonRow.X_AXIS or ButtonRow.Y_AXIS
     * @param glue        the glue determining the placement of the buttons,
     *                    either TOP_GLUE, BOTTOM_GLUE, LEFT_GLUE, RIGHT_GLUE,
     *                    or NO_GLUE@param orientation
     */
    public ButtonRow(Action[] actions, int orientation, int glue) {
        this(actions, orientation, glue, null);
    }

    private ButtonRow(Action[] actions, int orientation, int glue, JComponent extraComponent) {
        BoxLayout bl = new BoxLayout(this, orientation);
        setLayout(bl);
        final int sepLength = actions.length - 1;
        _buttons = new JButton[actions.length];
        Component[] separators = new Component[sepLength];
        for (int i = 0; i < actions.length; i++) {
            if (actions[i].getValue(LimeAction.ICON_NAME) != null) {
                _buttons[i] = new IconButton(actions[i]);
            } else {
                _buttons[i] = new JButton(actions[i]);
            }
        }
        int i = 0;
        Dimension d;
        if (orientation == BoxLayout.X_AXIS) {
            d = new Dimension(BUTTON_SEP, 0);
            while (i < (sepLength)) {
                separators[i] = Box.createRigidArea(d);
                i++;
            }
        }
        // otherwise the orientation should be BoxLayout.Y_AXIS
        else {
            d = new Dimension(0, BUTTON_SEP);
            while (i < (sepLength)) {
                separators[i] = Box.createRigidArea(d);
                i++;
            }
        }
        i = 0;
        if (glue == TOP_GLUE && orientation == Y_AXIS)
            add(Box.createVerticalGlue());
        else if (glue == LEFT_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        else if (glue == NO_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        while (i < actions.length) {
            add(_buttons[i]);
            if (i < sepLength) {
                add(separators[i]);
            }
            i++;
        }
        if (glue == BOTTOM_GLUE && orientation == Y_AXIS)
            add(Box.createVerticalGlue());
        else if (glue == RIGHT_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        else if (glue == NO_GLUE && orientation == X_AXIS)
            add(Box.createHorizontalGlue());
        if (extraComponent != null) {
            add(extraComponent);
        }
    }

    /**
     * Assigns listeners to each button in the row.
     *
     * @param listeners the array of listeners to assign to the buttons
     */
    private void setListeners(EventListener[] listeners) {
        int i = 0;
        int length = _buttons.length;
        int listenLength = listeners.length;
        if (listenLength <= length) {
            while (i < length) {
                if (listeners[i] instanceof ActionListener) {
                    _buttons[i].addActionListener((ActionListener) listeners[i]);
                } else if (listeners[i] instanceof MouseAdapter) {
                    _buttons[i].addMouseListener((MouseAdapter) listeners[i]);
                }
                i++;
            }
        }
    }

    /**
     * This method allows access to specific buttons in the button row.
     *
     * @param index the index of the button to retrieve
     * @return the <tt>JButton</tt> at that index
     * @throws ArrayIndexOutOfBoundsException if the index is out of bounds
     */
    public JButton getButtonAtIndex(int index) {
        if (index >= _buttons.length)
            throw new ArrayIndexOutOfBoundsException();
        return _buttons[index];
    }

    /**
     * Sets the button at the specified index to be enabled or disabled.
     *
     * @param buttonIndex the index of the button to enable or disable
     * @param enabled     whether to enable or disable the button
     */
    public void setButtonEnabled(final int buttonIndex, final boolean enabled) {
        if (buttonIndex >= _buttons.length)
            throw new ArrayIndexOutOfBoundsException();
        _buttons[buttonIndex].setEnabled(enabled);
    }

    /**
     * Sets the enabled/disabled of all buttons in the row.
     *
     * @param enabled the enabled/disabled state of the buttons
     */
    public void setButtonsEnabled(final boolean enabled) {
        for (JButton button : _buttons) {
            button.setEnabled(enabled);
        }
    }
}
