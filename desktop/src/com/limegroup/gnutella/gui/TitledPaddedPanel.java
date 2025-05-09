/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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

import com.frostwire.gui.theme.ThemeMediator;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * This is a reusable class that creates a titled panel with the specified
 * title and the specified padding both surrounding the panel and inside
 * the panel.  This panel also uses <tt>BoxLayout</tt> on the outer panels
 * for layout purposes.<p>
 * <p>
 * The inner panel also defaults to <tt>BoxLayout</tt>, but the user can change
 * the layout with the overridden setLayout method.
 */
public class TitledPaddedPanel extends JPanel {
    /**
     * Constant for specifying an x axis orientation for the layout.
     */
    public static final int X_AXIS = 20;
    /**
     * Constant for specifying a y axis orientation for the layout.
     */
    public static final int Y_AXIS = 21;
    /**
     * The number of pixels making up the margin of a titled panel.
     */
    private static final int TITLED_MARGIN = 6;
    /**
     * The number of pixels in the margin of a padded panel.
     */
    private static final int OUT_MARGIN = 6;
    /**
     * The inner panel that components are added to.
     */
    private final BoxPanel _mainPanel;
    /**
     * The <tt>TitledBorder</tt> for the panel, stored to allow changing
     * the title.
     */
    private final TitledBorder _titledBorder;

    /**
     * Creates a <tt>TitledPaddedPanel</tt> with the specified title
     * and the specified outer and inner padding.  The underlying
     * <tt>JPanel</tt> uses <tt>BoxLayout</tt> oriented along the y axis.
     *
     * @param title    the title of the panel
     * @param outerPad the padding to use on the outside of the titled border
     * @param innerPad the padding to use on the inside of the titled border
     */
    private TitledPaddedPanel(String title, int outerPad, int innerPad) {
        JPanel titlePanel = new JPanel();
        _mainPanel = new BoxPanel();
        BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
        BoxLayout titleLayout = new BoxLayout(titlePanel, BoxLayout.Y_AXIS);
        // the titled border adds padding above and below the title
        Border outerBorder = BorderFactory.createEmptyBorder((outerPad > 6) ? outerPad - 6 : 0, outerPad, outerPad, outerPad);
        _titledBorder = ThemeMediator.createTitledBorder(title);
        Border innerBorder = BorderFactory.createEmptyBorder((innerPad > 6) ? innerPad - 6 : 0, innerPad, innerPad, innerPad);
        setLayout(layout);
        titlePanel.setLayout(titleLayout);
        setBorder(outerBorder);
        titlePanel.setBorder(_titledBorder);
        _mainPanel.setBorder(innerBorder);
        titlePanel.putClientProperty(ThemeMediator.SKIN_PROPERTY_DARK_BOX_BACKGROUND, Boolean.TRUE);
        titlePanel.add(_mainPanel);
        super.add(titlePanel);
    }

    /**
     * Creates a <tt>TitledPaddedPanel</tt> with the empty string as
     * its title.
     */
    public TitledPaddedPanel() {
        this("", OUT_MARGIN, TITLED_MARGIN);
    }

    /**
     * Sets the title displayed in the panel.
     *
     * @param title the title to use for the panel.
     */
    public void setTitle(String title) {
        _titledBorder.setTitle(title);
    }

    /**
     * Overrides the add(Component comp) method in the <tt>Container</tt>
     * class, adding the <tt>Component</tt> to the inner panel.
     *
     * @param comp the <tt>Component</tt> to add
     */
    @Override
    public Component add(Component comp) {
        _mainPanel.addRight((JComponent) comp);
        return comp;
    }
}
