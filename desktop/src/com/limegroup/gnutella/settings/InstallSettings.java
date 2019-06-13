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
     * Whether or not the 'Choose your Save directory' question has
     * been asked.
     */
    public static final BooleanSetting SAVE_DIRECTORY = FACTORY.createBooleanSetting("SAVE_DIRECTORY", false);
    /**
     * Whether or not the 'Choose your speed' question has been asked.
     */
    public static final BooleanSetting SPEED = FACTORY.createBooleanSetting("SPEED", false);
    /**
     * Whether or not the 'Scan for files' question has been asked.
     */
    public static final BooleanSetting SCAN_FILES = FACTORY.createBooleanSetting("SCAN_FILES", false);
    /**
     * Whether or not the 'Start on startup' question has been asked.
     */
    public static final BooleanSetting START_STARTUP = FACTORY.createBooleanSetting("START_STARTUP", false);
    /**
     * Whether or not the 'Choose your language' question has been asked.
     */
    public static final BooleanSetting LANGUAGE_CHOICE = FACTORY.createBooleanSetting("LANGUAGE_CHOICE", false);
    /**
     * Whether or not the firewall warning question has been asked.
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