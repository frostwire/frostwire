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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.util.CommonUtils;

import java.io.File;

/**
 * Settings for messages
 *
 * @author gubatron
 * @author aldenml
 */
public final class UpdateSettings extends LimeProps {
    /**
     * Whether or not it should download updates automatically. This does not mean it will install the update,
     * it'll just download the installer for the user and then let the user know next time he/she restarts
     * FrostWire.
     */
    public static final BooleanSetting AUTOMATIC_INSTALLER_DOWNLOAD = FACTORY.createBooleanSetting("AUTOMATIC_INSTALLER_DOWNLOAD", true);
    public static final File UPDATES_DIR = new File(CommonUtils.getUserSettingsDir(), "updates");

    private UpdateSettings() {
    }
}
