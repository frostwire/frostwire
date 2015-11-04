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

package com.limegroup.gnutella.gui.options;

import org.limewire.service.ErrorService;

import com.limegroup.gnutella.gui.options.panes.AbstractPaneItem;

/**
 * Static factory class that creates the option panes based on their keys.
 * <p>
 * This class constructs all of the elements of the options window.  To add
 * a new option, this class should be used.  This class allows for options
 * to be added to already existing panes as well as for options to be added
 * to new panes that you can also add here.  To add a new top-level pane,
 * create a new <tt>OptionsPaneImpl</tt> and call the addOption method.
 * To add option items to that pane, add subclasses of
 * <tt>AbstractPaneItem</tt>.
 */
class OptionsPaneFactory {
    
    /**
     * Constructs a new OptionsPaneFactory.
     *
     * Due to intermixing within Saved & Shared pane items, these two need special
     * setups.
     */
    OptionsPaneFactory() {
    }
    
	/**
	 * Creates the options pane for a key. 
	 * @param key keys are listed in {@link OptionsConstructor}.
	 * @return
	 */
	OptionsPane createOptionsPane(OptionsTreeNode node) {
	    Class<? extends AbstractPaneItem>[] clazzes = node.getClasses();
	    if (clazzes != null) {
            final OptionsPane pane = new OptionsPaneImpl(node.getTitleKey());
	        for (Class<? extends AbstractPaneItem> clazz : clazzes) {
                try {
                    pane.add(clazz.newInstance());
                } catch (Exception e) {
                    ErrorService.error(e);
                }
	        }
	        return pane;
	    } else {
	        throw new IllegalArgumentException("no options pane for this key: " + node.getTitleKey());
		}
	}

}
