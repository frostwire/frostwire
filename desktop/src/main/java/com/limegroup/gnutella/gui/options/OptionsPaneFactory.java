/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.options.panes.AbstractPaneItem;
import com.frostwire.service.ErrorService;

/**
 * Static factory class that creates the option panes based on their keys.
 * <p>
 * This class constructs all of the elements of the options window.  To add
 * a new option, this class should be used.  This class allows for options
 * to be added to already existing panes as well as for options to be added
 * to new panes that you can also add here.  To add a new top-level pane,
 * create a new `OptionsPaneImpl` and call the addOption method.
 * To add option items to that pane, add subclasses of
 * `AbstractPaneItem`.
 */
class OptionsPaneFactory {
    /**
     * Constructs a new OptionsPaneFactory.
     * <p>
     * Due to intermixing within Saved & Shared pane items, these two need special
     * setups.
     */
    OptionsPaneFactory() {
    }

    /**
     * Creates the options pane for a key.
     */
    OptionsPane createOptionsPane(OptionsTreeNode node) {
        Class<? extends AbstractPaneItem>[] clazzes = node.getClasses();
        if (clazzes != null) {
            final OptionsPane pane = new OptionsPaneImpl(node.getTitleKey());
            for (Class<? extends AbstractPaneItem> clazz : clazzes) {
                try {
                    pane.add(clazz.getDeclaredConstructor().newInstance());
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
