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

import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.options.panes.PaneItem;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides a skeletal implementation of the `OptionsPane`
 * interface, providing common functionality to its subclasses.<p>
 * <p>
 * It contains an `ArrayList` of `PaneItem` instances to
 * forward any request to apply the current options.
 */
final class OptionsPaneImpl implements OptionsPane {
    /**
     * Constant for the `Container` that elements are added to. This
     * is implemented as a `BoxPanel`.
     */
    private final Container CONTAINER = new BoxPanel();
    /**
     * Constant for the `ArrayList` that contains all of the
     * `PaneItem` instances associated with this panel.
     */
    private final List<PaneItem> PANE_ITEMS_LIST = new ArrayList<>();
    /**
     * `String` for the name of this panel.  This name is used as the
     * key for identifying this panel in the `CardLayout`.
     */
    private final String _name;

    /**
     * This sole constructor overrides the public accessibility of the
     * default constructor and is usually called implicitly by subclasses.
     *
     * @param name the unique identifying name of this
     *             `AbstractOptionsPane`
     */
    OptionsPaneImpl(final String name) {
        _name = name;
    }

    /**
     * Implements the OptionsPane interface.<p>
     * <p>
     * Returns the name associated with this `OptionsPane`.
     *
     * @return the name associated with this `OptionsPane`
     */
    public String getName() {
        return _name;
    }

    /**
     * Implements the OptionsPane interface.<p>
     * <p>
     * Returns the `Container` instance associated with this
     * `OptionsPane`.
     *
     * @return the `Container` associated with this `OptionsPane`
     */
    public Container getContainer() {
        return CONTAINER;
    }

    /**
     * Implements the OptionsPane interface.<p>
     * <p>
     * Sets the options for each `PaneItem` instance in the
     * `ArrayList` of `PaneItem`s when the window is shown.
     */
    public void initOptions() {
        for (PaneItem currentItem : PANE_ITEMS_LIST) {
            currentItem.initOptions();
        }
    }

    /**
     * Implements the OptionsPane interface.<p>
     * <p>
     * Applies the currently selected options to the `ArrayList` of
     * `PaneItem` instances that have been added to this panel.
     *
     * @return <code>true</code> if one the changed settings requires a restart
     * of the application.
     * @throws IOException if the options could not be fully applied
     */
    public boolean applyOptions() throws IOException {
        boolean restartRequired = false;
        for (PaneItem currentItem : PANE_ITEMS_LIST) {
            restartRequired |= currentItem.applyOptions();
        }
        return restartRequired;
    }

    /**
     * Determines if any of the panes stored within this OptionPane
     * require saving.
     */
    public boolean isDirty() {
        for (PaneItem currentItem : PANE_ITEMS_LIST) {
            if (currentItem.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add the `Container`s of the `PaneItem` object to the
     * `OptionsPane` and also <i>registers</i> that pane
     * with this class, which means that it is added to the `ArrayList`
     * of contained `PaneItem` instances.
     *
     * @param item the `PaneItem` instance to add
     */
    public final void add(PaneItem item) {
        PANE_ITEMS_LIST.add(item);
        CONTAINER.add(item.getContainer());
    }
}
