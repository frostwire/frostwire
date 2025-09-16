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
