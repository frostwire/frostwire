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
