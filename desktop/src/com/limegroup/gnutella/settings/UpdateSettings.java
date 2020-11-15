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
