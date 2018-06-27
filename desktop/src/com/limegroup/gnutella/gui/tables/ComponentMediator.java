/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.tables;

import com.limegroup.gnutella.gui.RefreshListener;

import javax.swing.*;

/**
 * A common interface for GUI components
 * Allows adding/removing objects, and supports
 * embedding buttons that are 'attached' to the information.
 * @author Sam Berlin
 */
public interface ComponentMediator<T> extends RefreshListener, MouseObserver {
    
    /**
     * Signifies an object was added to this component
     */
    void add(T o);
    
    /**
     * Signifies an object was removed from this component.
     */
    void remove(T o);
    
    /**
     * Signifies an object in this component was updated.
     */
    void update(T o);
    
    /**
     * Event for the 'action' key being pressed.
     */
    void handleActionKey();
    
    /**
     * Returns the underlying component that this Mediator handles
     */
    JComponent getComponent();
    
    /**
     * Removes whatever is selected from the component
     */
    void removeSelection();
    
    /**
     * Event for when something (such as a row) is selected.
     */
    void handleSelection(int row);
    
    /**
     * Event for when nothing is selected.
     */
    void handleNoSelection();
    
    /**
     * Handles setting/unsetting  the status of the buttons
     * that this Mediator controls.
     */
    void setButtonEnabled(int buttonIdx, boolean enabled);
}
    
    
    
    
    
    