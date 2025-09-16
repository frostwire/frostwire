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

import com.limegroup.gnutella.gui.options.OptionsConstructor;
import org.limewire.setting.BooleanSetting;
import org.limewire.setting.FileSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.StringSetting;
import org.limewire.util.CommonUtils;
import com.frostwire.util.OSUtils;

import java.io.File;

/**
 * Settings for FrostWire application
 */
public class ApplicationSettings extends LimeProps {
    /**
     * Specifies whether the program has been installed, either by
     * a third-party installer, or by our own.  This is the old value for
     * legacy InstallShield installers that set the save directory and the
     * connection speed.
     */
    public static final BooleanSetting INSTALLED = FACTORY.createBooleanSetting("INSTALLED", false);
    /**
     * The width that the application should be.
     */
    public static final IntSetting APP_WIDTH = FACTORY.createIntSetting("APP_WIDTH", 1200);
    /**
     * The height that the application should be.
     */
    public static final IntSetting APP_HEIGHT = FACTORY.createIntSetting("APP_HEIGHT", 700);
    /**
     * A flag for whether or not the application has been run one
     * time before this.
     */
    public static final BooleanSetting RUN_ONCE = FACTORY.createBooleanSetting("RUN_ONCE", false);
    /**
     * The x position of the window for the next time the application
     * is started.
     */
    public static final IntSetting WINDOW_X = (IntSetting) FACTORY.createIntSetting("WINDOW_X", 0).setAlwaysSave(true);
    /**
     * The y position of the window for the next time the application
     * is started.
     */
    public static final IntSetting WINDOW_Y = (IntSetting) FACTORY.createIntSetting("WINDOW_Y", 0).setAlwaysSave(true);
    /**
     * Setting for whether or not LW should start maximized.
     */
    public static final BooleanSetting MAXIMIZE_WINDOW = FACTORY.createBooleanSetting("MAXIMIZE_WINDOW", false);
    /**
     * A flag for whether or not to display the system
     * tray icon while the application is visible.
     */
    public static final BooleanSetting DISPLAY_TRAY_ICON = FACTORY.createBooleanSetting("DISPLAY_TRAY_ICON", true);
    /**
     * The language to use for the application.
     */
    public static final StringSetting LANGUAGE = FACTORY.createStringSetting("LANGUAGE", System.getProperty("user.language", ""));
    /**
     * The country to use for the application.
     */
    public static final StringSetting COUNTRY = FACTORY.createStringSetting("COUNTRY", System.getProperty("user.country", ""));
    /**
     * The locale variant to use for the application.
     */
    public static final StringSetting LOCALE_VARIANT = FACTORY.createStringSetting("LOCALE_VARIANT", System.getProperty("user.variant", ""));
    /**
     * Setting for whether or not to create an additional manual GC thread.
     */
    public static final BooleanSetting AUTOMATIC_MANUAL_GC = FACTORY.createBooleanSetting("AUTOMATIC_MANUAL_GC", OSUtils.isMacOSX());
    /**
     * Enable the MagnetClipboardListener on non Windows and Mac OS
     * systems
     */
    public static final BooleanSetting MAGNET_CLIPBOARD_LISTENER = FACTORY.createBooleanSetting("MAGNET_CLIPBOARD_LISTENER", !OSUtils.isWindows() && !OSUtils.isAnyMac());
    /**
     * Whether LimeWire should handle magnets.
     */
    public static final BooleanSetting HANDLE_MAGNETS = FACTORY.createBooleanSetting("HANDLE_MAGNETS", true);
    /**
     * Whether LimeWire should handle torrents.
     */
    public static final BooleanSetting HANDLE_TORRENTS = FACTORY.createBooleanSetting("HANDLE_TORRENTS", true);
    /**
     * The last directory used for opening a file chooser.
     */
    public static final FileSetting LAST_FILECHOOSER_DIRECTORY = FACTORY.createFileSetting("LAST_FILECHOOSER_DIR", new File("")).setAlwaysSave(true);
    public static final IntSetting FILECHOOSER_WIDTH = (IntSetting) FACTORY.createIntSetting("FILECHOOSER_WIDTH", 900).setAlwaysSave(true);
    public static final IntSetting FILECHOOSER_HEIGHT = (IntSetting) FACTORY.createIntSetting("FILECHOOSER_HEIGHT", 700).setAlwaysSave(true);
    public static final IntSetting FILECHOOSER_X_POS = (IntSetting) FACTORY.createIntSetting("FILECHOOSER_X_POS", -1).setAlwaysSave(true);
    public static final IntSetting FILECHOOSER_Y_POS = (IntSetting) FACTORY.createIntSetting("FILECHOOSER_Y_POS", -1).setAlwaysSave(true);
    /**
     * A flag for whether or not the application should be minimized
     * to the system tray on windows.
     */
    public static final BooleanSetting MINIMIZE_TO_TRAY = FACTORY.createBooleanSetting("MINIMIZE_TO_TRAY", OSUtils.supportsTray());
    public static final BooleanSetting SHOW_HIDE_EXIT_DIALOG = FACTORY.createBooleanSetting("SHOW_HIDE_EXIT_DIALOG", true);
    public static final FileSetting APP_DATABASES_PATH = FACTORY.createFileSetting("APP_DATABASES_PATH", new File(CommonUtils.getUserSettingsDir(), "dbs"));
    public static final IntSetting GUI_TABLES_FONT_SIZE = (IntSetting) FACTORY.createIntSetting("GUI_TABLES_FONT_SIZE", 0).setAlwaysSave(true);
    public static final StringSetting OPTIONS_LAST_SELECTED_KEY = FACTORY.createStringSetting("OPTIONS_LAST_SELECTED_KEY", OptionsConstructor.BITTORRENT_BASIC_KEY);

    private ApplicationSettings() {
    }

    /**
     * Gets the current language setting.
     */
    public static String getLanguage() {
        String lc = LANGUAGE.getValue();
        String cc = COUNTRY.getValue();
        String lv = LOCALE_VARIANT.getValue();
        String lang = lc;
        if (cc != null && !cc.equals(""))
            lang += "_" + cc;
        if (lv != null && !lv.equals(""))
            lang += "_" + lv;
        return lang;
    }
}
