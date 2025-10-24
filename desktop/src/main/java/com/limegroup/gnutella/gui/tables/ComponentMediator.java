/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
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

package com.limegroup.gnutella.gui.tables;

import com.limegroup.gnutella.gui.RefreshListener;

import javax.swing.*;

/**
 * A common interface for GUI components
 * Allows adding/removing objects, and supports
 * embedding buttons that are 'attached' to the information.
 *
 * @author Sam Berlin
 */
public interface ComponentMediator<T> extends RefreshListener, MouseObserver {
    /**
     * Signifies an object was added to this component
     */
    @SuppressWarnings("unused")
    void add(T o);

    /**
     * Signifies an object was removed from this component.
     */
    @SuppressWarnings("unused")
    void remove(T o);

    /**
     * Signifies an object in this component was updated.
     */
    @SuppressWarnings("unused")
    void update(T o);

    /**
     * Event for the 'action' key being pressed.
     */
    @SuppressWarnings("unused")
    void handleActionKey();

    /**
     * Returns the underlying component that this Mediator handles
     */
    @SuppressWarnings("unused")
    JComponent getComponent();

    /**
     * Removes whatever is selected from the component
     */
    @SuppressWarnings("unused")
    void removeSelection();

    /**
     * Event for when something (such as a row) is selected.
     */
    @SuppressWarnings("unused")
    void handleSelection(int row);

    /**
     * Event for when nothing is selected.
     */
    @SuppressWarnings("unused")
    void handleNoSelection();

    /**
     * Handles setting/unsetting  the status of the buttons
     * that this Mediator controls.
     */
    @SuppressWarnings("unused")
    void setButtonEnabled(int buttonIdx, boolean enabled);
}
    
    
    
    
    
    