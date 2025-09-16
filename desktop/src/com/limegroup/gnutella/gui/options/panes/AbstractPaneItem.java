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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * This class provides a skeletal implementation of the <tt>PaneItem</tt>
 * interface.<p>
 * <p>
 * It provides the basic implementation for displaying one option within
 * a larger window of options. Each <tt>AbstractPaneItem</tt> has a titled
 * border and a label describing the option.  The label is followed by
 * standardized spacing.<p>
 * <p>
 * It includes several convenience methods that subclasses may us to
 * simplify panel construction.<p>
 * <p>
 * Subclasses only need to override the applyOptions() method for storing
 * options to disk.
 */
public abstract class AbstractPaneItem implements PaneItem {
    /**
     * The container that elements in the pane are added to.
     */
    private final TitledPaddedPanel CONTAINER = new TitledPaddedPanel();

    /**
     * This sole constructor overrides the the public accessibility of the
     * default constructor and is usually called implicitly.
     */
    AbstractPaneItem(final String title, final String text) {
        this(title, text, null);
    }

    private AbstractPaneItem(final String title, final String text, String url) {
        CONTAINER.setTitle(title);
        // make sure the panel always expands to the full width of the dialog
        add(Box.createHorizontalGlue());
        int LABEL_WIDTH = 415;
        JComponent label = new MultiLineLabel(text, LABEL_WIDTH, true /* resizable */);
        GUIUtils.restrictSize(label, SizePolicy.RESTRICT_HEIGHT);
        add(label);
        add(getVerticalSeparator());
        if (url != null) {
            add(new URLLabel(url, I18n.tr("Learn more about this option...")));
            add(getVerticalSeparator());
        }
    }

    /**
     * Implements the <tt>PaneItem</tt> interface. <p>
     * <p>
     * Returns the <tt>Container</tt> for this set of options.
     *
     * @return the <tt>Container</tt> for this set of options
     */
    public Container getContainer() {
        return CONTAINER;
    }

    /**
     * Implements the <tt>PaneItem</tt> interface. <p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     * <p>
     * Subclasses must define this method to set their initial options
     * when the options window is shown.
     */
    public abstract void initOptions();

    /**
     * Implements the <tt>PaneItem</tt> interface. <p>
     * <p>
     * Applies the options currently set in this <tt>PaneItem</tt>.<p>
     * <p>
     * Subclasses must define this method to apply their specific options.
     *
     * @throws IOException if the options could not be fully applied
     */
    public abstract boolean applyOptions() throws IOException;

    /**
     * Adds the specified <tt>Component</tt> to the enclosed <tt>Container</tt>
     * instance.
     *
     * @param comp the <tt>Component</tt> to add
     */
    final void add(Component comp) {
        CONTAINER.add(comp);
    }

    /**
     * Returns a <tt>Component</tt> standardly sized for horizontal separators.
     *
     * @return the constant <tt>Component</tt> used as a standard horizontal
     * separator
     */
    final Component getHorizontalSeparator() {
        return Box.createRigidArea(new Dimension(6, 0));
    }

    /**
     * Returns a <tt>Component</tt> standardly sized for vertical separators.
     *
     * @return the constant <tt>Component</tt> used as a standard vertical
     * separator
     */
    final Component getVerticalSeparator() {
        return Box.createRigidArea(new Dimension(0, 6));
    }
}
