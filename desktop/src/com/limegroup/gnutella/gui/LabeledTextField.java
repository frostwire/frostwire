/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;

/**
 * This class creates a text field with a label next to it.
 */
public class LabeledTextField extends JPanel {
    /**
     * The <tt>JTextField</tt> part of this component.
     */
    private final JTextField _field;

    /**
     * Constructor with the specified width of the text box in columns
     *
     * @param lab       the label for the text field
     * @param textWidth the number of columns in the text field
     */
    public LabeledTextField(String lab, int textWidth) {
        this(lab, textWidth, -1, 500);
    }

    /**
     * Constructor with the specified width of the text box and a
     * specified margin in pixels to the left of the labeled field.
     *
     * @param lab       the label for the text field
     * @param textWidth the number of columns in the text field
     * @param strutSize the size (in pixels) of the margin to the left of
     *                  the labeled field
     * @param width     The Width of the textfield.
     */
    public LabeledTextField(String lab, int textWidth, int strutSize,
                            int width) {
        BoxLayout layout = new BoxLayout(this, BoxLayout.X_AXIS);
        setLayout(layout);
        Dimension d = new Dimension(width, 28);
        setPreferredSize(d);
        setMaximumSize(d);
        JLabel label = new JLabel(lab);
        _field = new LimeTextField("", textWidth);
        if (strutSize > -1)
            add(Box.createHorizontalStrut(strutSize));
        add(label);
        add(Box.createHorizontalStrut(GUIConstants.SEPARATOR));
        add(_field);
    }

    /**
     * Returns the String contained in the <tt>JTextField</tt>.
     *
     * @return the text in the <tt>JTextField</tt>
     */
    public String getText() {
        return _field.getText();
    }

    /**
     * Sets the String contained in the <tt>JTextField</tt>.
     *
     * @param text the text to place in the <tt>JTextField</tt>
     */
    public void setText(String text) {
        _field.setText(text);
    }

    /**
     * Sets the tooltip for the JTextField.
     *
     * @param text the text to set as the tooltip for the <tt>JTextField</tt>
     */
    public void setToolTipText(String text) {
        _field.setToolTipText(text);
    }

    public void setEnabled(boolean enabled) {
        _field.setEnabled(enabled);
    }

    @Override
    public synchronized void addKeyListener(KeyListener l) {
        super.addKeyListener(l);
        _field.addKeyListener(l);
    }

    public void requestFocus() {
        _field.requestFocusInWindow();
    }
}