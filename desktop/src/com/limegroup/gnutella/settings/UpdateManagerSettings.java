/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.settings;

import com.frostwire.util.UrlUtils;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.BooleanSetting;
import org.limewire.setting.StringSetting;
import org.limewire.util.OSUtils;

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
    /**
     * URL to feed the Slideshow with the promotional FrostClick overlays
     */
    public static final StringSetting OVERLAY_SLIDESHOW_JSON_URL = FACTORY.createStringSetting("OVERLAY_SLIDESHOW_JSON_URL", "https://update.frostwire.com/o2.php?from=desktop&version=" + FrostWireUtils.getFrostWireVersion() + "&build=" + FrostWireUtils.getBuildNumber() + "&os=" + UrlUtils.encode(OSUtils.getFullOS()));

    private UpdateManagerSettings() {
    }
}
