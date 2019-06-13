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

package com.limegroup.gnutella.gui.util;

import org.limewire.setting.IntSetting;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Keeps track of the divider location changes of a {@link JSplitPane} and updates
 * an {@link IntSetting}.
 */
public class DividerLocationSettingUpdater {
    private static final LocationChangeListener propertyListener = new LocationChangeListener();

    /**
     * Adds a property change listener to the split pane and updates the int
     * setting when the divider location changes.
     * <p>
     * Also sets the divider location to the value of the setting.
     *
     * @param pane
     * @param setting
     */
    public static void install(JSplitPane pane, IntSetting setting) {
        pane.setDividerLocation(setting.getValue());
        pane.putClientProperty(propertyListener, setting);
        pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, propertyListener);
    }

    private static class LocationChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            JSplitPane pane = (JSplitPane) evt.getSource();
            IntSetting setting = (IntSetting) pane.getClientProperty(this);
            setting.setValue(pane.getDividerLocation());
        }
    }
}
