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
