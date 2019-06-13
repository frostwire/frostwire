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

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.util.FrostWireUtils;

/**
 * Provides methods to display notifications for common settings problems
 */
public class SettingsWarningManager {
    /**
     * Warn about load/save problems
     */
    public static void checkSettingsLoadSaveFailure() {
        // TODO: implement actual UI notification
        if (FrostWireUtils.hasSettingsLoadSaveFailures()) {
            //msg = I18n.tr("FrostWire has encountered problems in managing your settings.  Your settings changes may not be saved on shutdown.");
            FrostWireUtils.resetSettingsLoadSaveFailures();
        } else if (ResourceManager.hasLoadFailure()) {
            //msg = I18n.tr("FrostWire has encountered problems in loading your settings.  FrostWire will attempt to use the default values; however, may behave unexpectedly.");
            ResourceManager.resetLoadFailure();
        }
    }
}
