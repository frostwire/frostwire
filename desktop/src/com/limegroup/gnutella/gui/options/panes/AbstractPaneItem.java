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
