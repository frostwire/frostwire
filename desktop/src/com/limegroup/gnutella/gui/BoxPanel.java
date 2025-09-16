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

/**
 * This class is simply a JPanel that uses a BoxLayout with the orientation
 * specified in the constructor.  The default constructor creates a panel
 * oriented along the y axis.
 */
public class BoxPanel extends JPanel {
    /**
     * Constant for specifying that the underlying <tt>BoxLayout</tt> should
     * be oriented along the x axis.
     */
    public static final int X_AXIS = BoxLayout.X_AXIS;
    /**
     * Constant for specifying that the underlying <tt>BoxLayout</tt> should
     * be oriented along the y axis.
     */
    public static final int Y_AXIS = BoxLayout.Y_AXIS;
    private static final Dimension HORIZONTAL_COMPONENT_GAP = new Dimension(6, 0);
    private static final Dimension VERTICAL_COMPONENT_GAP = new Dimension(0, 6);

    /**
     * Creates a default <tt>BoxPanel</tt> with a <tt>BoxLayout</tt> oriented
     * along the y axis.
     */
    public BoxPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /**
     * Creates a <tt>BoxPanel</tt> with a <tt>BoxLayout</tt> that uses the
     * specified orientation.
     *
     * @param orientation the orientation to use for the layout, which should
     *                    be either BoxPanel.X_AXIS or BoxPanel.Y_AXIS
     * @throws IllegalArgumentException if the <tt>orientation</tt> is not
     *                                  a valid <tt>BoxPanel</tt> orientation
     */
    public BoxPanel(int orientation) {
        if (orientation != X_AXIS && orientation != Y_AXIS)
            throw new IllegalArgumentException("Illegal BoxPanel orientation");
        setLayout(new BoxLayout(this, orientation));
    }

    /**
     * Sets the orientation that the panel uses for laying out components.
     *
     * @param orientation the orientation to use for the layout, which should
     *                    be either BoxPanel.X_AXIS or BoxPanel.Y_AXIS
     * @throws IllegalArgumentException if the <tt>orientation</tt> is not
     *                                  a valid <tt>BoxPanel</tt> orientation
     */
    void setOrientation(@SuppressWarnings("SameParameterValue") int orientation) {
        if (orientation != X_AXIS && orientation != Y_AXIS)
            throw new IllegalArgumentException("Illegal BoxPanel orientation");
        setLayout(new BoxLayout(this, orientation));
    }

    public void addHorizontalComponentGap() {
        add(Box.createRigidArea(BoxPanel.HORIZONTAL_COMPONENT_GAP));
    }

    public void addVerticalComponentGap() {
        add(Box.createRigidArea(BoxPanel.VERTICAL_COMPONENT_GAP));
    }

    void addRight(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        super.add(component);
    }
}
