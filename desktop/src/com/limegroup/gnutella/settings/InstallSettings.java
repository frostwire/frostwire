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
import org.limewire.setting.IntSetting;
import org.limewire.setting.SettingsFactory;
import org.limewire.setting.StringSetting;

/**
 * Handles installation preferences.
 */
public final class InstallSettings extends LimeWireSettings {
    private static final InstallSettings INSTANCE = new InstallSettings();
    private static final SettingsFactory FACTORY = INSTANCE.getFactory();
    /**
     * Whether the 'Choose your Save directory' question has
     * been asked.
     */
    public static final BooleanSetting SAVE_DIRECTORY = FACTORY.createBooleanSetting("SAVE_DIRECTORY", false);
    /**
     * Whether the 'Choose your speed' question has been asked.
     */
    public static final BooleanSetting SPEED = FACTORY.createBooleanSetting("SPEED", false);
    /**
     * Whether the 'Scan for files' question has been asked.
     */
    public static final BooleanSetting SCAN_FILES = FACTORY.createBooleanSetting("SCAN_FILES", false);
    /**
     * Whether the 'Start on startup' question has been asked.
     */
    public static final BooleanSetting START_STARTUP = FACTORY.createBooleanSetting("START_STARTUP", false);
    /**
     * Whether the 'Choose your language' question has been asked.
     */
    public static final BooleanSetting LANGUAGE_CHOICE = FACTORY.createBooleanSetting("LANGUAGE_CHOICE", false);
    /**
     * Whether the firewall warning question has been asked.
     */
    public static final BooleanSetting FIREWALL_WARNING = FACTORY.createBooleanSetting("FIREWALL_WARNING", false);
    /**
     * Whether the association option has been asked
     */
    public static final IntSetting ASSOCIATION_OPTION = FACTORY.createIntSetting("ASSOCIATION_OPTION", 1);
    /**
     * Whether the association option has been asked
     */
    public static final BooleanSetting EXTENSION_OPTION = FACTORY.createBooleanSetting("EXTENSION_OPTION", false);
    public static final StringSetting LAST_FROSTWIRE_VERSION_WIZARD_INVOKED = FACTORY.createStringSetting("LAST_FROSTWIRE_VERSION_WIZARD_INVOKED", "");

    private InstallSettings() {
        super("installation.props", "FrostWire installs file");
    }
}