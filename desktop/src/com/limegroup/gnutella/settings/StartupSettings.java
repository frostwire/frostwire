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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;

/**
 * Settings for starting frostwire.
 */
public final class StartupSettings extends LimeProps {
    /**
     * Setting for whether or not to allow multiple instances of LimeWire.
     */
    public static final BooleanSetting ALLOW_MULTIPLE_INSTANCES = FACTORY.createBooleanSetting("ALLOW_MULTIPLE_INSTANCES", false);
    /**
     * A boolean flag for whether or not to start FrostWire on system startup.
     */
    public static final BooleanSetting RUN_ON_STARTUP = FACTORY.createBooleanSetting("RUN_ON_STARTUP", true);
    /**
     * Whether or not tips should be displayed on startup.
     */
    public static final BooleanSetting SHOW_TOTD = FACTORY.createBooleanSetting("SHOW_TOTD", false);

    private StartupSettings() {
    }
}
