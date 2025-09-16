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

package com.frostwire.gui.tabs;

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * This class provided a rudimentary implementation of key functions for
 * any tab in the primary window. Public for being accesed from frostwiregroup
 */
public abstract class AbstractTab implements Tab {
    /**
     * PropertyChangeSupport.
     */
    private final PropertyChangeSupport propertyChangeSupport;
    /**
     * Constant for the title of this tab.
     */
    private String title;
    /**
     * Constant for the tool tip for this tab.
     */
    private String toolTip;
    /**
     * <tt>Icon</tt> instance to use for this tab.
     */
    private Icon icon;

    /**
     * Constructs the elements of the tab.
     */
    AbstractTab(String title, String tooltip, String icon) {
        this.title = title;
        this.toolTip = tooltip;
        this.icon = GUIMediator.getThemeImage(icon);
        this.propertyChangeSupport = new PropertyChangeSupport(this);
    }

    public abstract JComponent getComponent();

    public String getTitle() {
        return title;
    }

    public String getToolTip() {
        return toolTip;
    }

    public Icon getIcon() {
        return icon;
    }

    public String toString() {
        return title + " tab";
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }
}
