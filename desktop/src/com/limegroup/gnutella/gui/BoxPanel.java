/*
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
