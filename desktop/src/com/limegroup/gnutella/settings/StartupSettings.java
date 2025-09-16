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
    public static final BooleanSetting SHOW_TOTD = FACTORY.createBooleanSetting("SHOW_TOTD", true);

    private StartupSettings() {
    }
}
