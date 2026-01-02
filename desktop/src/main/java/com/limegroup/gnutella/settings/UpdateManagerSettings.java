/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.BooleanSetting;
import org.limewire.setting.StringSetting;
import com.frostwire.util.OSUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UpdateManagerSettings extends LimeProps {
    /**
     * Whether or not to show promotion overlays
     */
    public static final BooleanSetting SHOW_PROMOTION_OVERLAYS = (BooleanSetting) FACTORY.createBooleanSetting("SHOW_PROMOTION_OVERLAYS", true).setAlwaysSave(true);
    public static final BooleanSetting SHOW_FROSTWIRE_RECOMMENDATIONS = (BooleanSetting) FACTORY.createBooleanSetting("SHOW_FROSTWIRE_RECOMMENDATIONS", true).setAlwaysSave(true);

    private static final String winappstore = OSUtils.isWindowsAppStoreInstall() ? "1" : "0";
    /**
     * URL to feed the Slideshow with the promotional FrostClick overlays
     */
    public static final StringSetting OVERLAY_SLIDESHOW_JSON_URL = FACTORY.createStringSetting("OVERLAY_SLIDESHOW_JSON_URL", "https://update.frostwire.com/o2.php?from=desktop&version=" + FrostWireUtils.getFrostWireVersion() + "&build=" + FrostWireUtils.getBuildNumber() + "&os=" + UrlUtils.encode(OSUtils.getFullOS()) + "&winappstore=" + winappstore);

    private UpdateManagerSettings() {
    }
}
