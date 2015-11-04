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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.io.IOException;

import javax.swing.Box;
import javax.swing.JComponent;

import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.MultiLineLabel;
import com.limegroup.gnutella.gui.TitledPaddedPanel;
import com.limegroup.gnutella.gui.URLLabel;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;

/**
 * This class provides a skeletal implementation of the <tt>PaneItem</tt>
 * interface.<p>
 *
 * It provides the basic implementation for displaying one option within
 * a larger window of options. Each <tt>AbstractPaneItem</tt> has a titled
 * border and a label describing the option.  The label is followed by
 * standardized spacing.<p>
 * 
 * It includes several convenience methods that subclasses may us to 
 * simplify panel construction.<p>
 *
 * Subclasses only need to override the applyOptions() method for storing
 * options to disk.
 */
public abstract class AbstractPaneItem implements PaneItem {
	
	/**
	 * The container that elements in the pane are added to.
	 */
	private final TitledPaddedPanel CONTAINER = new TitledPaddedPanel();

	private final int LABEL_WIDTH = 415;
	
	/**
	 * This sole constructor overrides the the public accessibility of the 
	 * default constructor and is usually called implicitly.
	 *
	 * @param key the key for obtaining the locale-specific values for
	 *  displayed strings
	 */
	protected AbstractPaneItem(final String title, final String text) {
        this(title, text, null);
    }
    
    protected AbstractPaneItem(final String title, final String text, String url) {
		CONTAINER.setTitle(title);
		
		// make sure the panel always expands to the full width of the dialog
		add(Box.createHorizontalGlue());
		
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
	 *
	 * Returns the <tt>Container</tt> for this set of options.
	 *
	 * @return the <tt>Container</tt> for this set of options
	 */
	public Container getContainer() {
		return CONTAINER;
	}

	/**
	 * Implements the <tt>PaneItem</tt> interface. <p>
	 *
	 * Sets the options for the fields in this <tt>PaneItem</tt> when the 
	 * window is shown.
	 *
	 * Subclasses must define this method to set their initial options 
	 * when the options window is shown.
	 */
	public abstract void initOptions();

	/**
	 * Implements the <tt>PaneItem</tt> interface. <p>
	 *
	 * Applies the options currently set in this <tt>PaneItem</tt>.<p>
	 *
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
	protected final void add(Component comp) {
		CONTAINER.add(comp);
	}
	
	/**
	 * Returns a <tt>Component</tt> standardly sized for horizontal separators.
	 *
	 * @return the constant <tt>Component</tt> used as a standard horizontal
	 *         separator
	 */
	protected final Component getHorizontalSeparator() {
		return Box.createRigidArea(new Dimension(6, 0));
	}

	/**
	 * Returns a <tt>Component</tt> standardly sized for vertical separators.
	 *
	 * @return the constant <tt>Component</tt> used as a standard vertical
	 *         separator
	 */
	protected final Component getVerticalSeparator() {
		return Box.createRigidArea(new Dimension(0, 6));
	}
}
