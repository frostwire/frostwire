/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.android.gui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.util.SystemUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulate the logic to manage the theme of the application.
 * Created by gubatron on 6/7/14.
 */
public class ThemeManager {
    private static Map<String, Integer> theme_entry_to_int_mode_map;

    private static final Object theme_entry_to_int_mode_map_lock = new Object();
    private static Map<Integer, String> theme_int_mode_to_entry_map;

    private static final Object theme_int_mode_to_entry_map_lock = new Object();

    public interface OnThemeLoadedUIThreadCallback {
        void onThemeLoaded(int themeMode);
    }

    /**
     * If called from an UI thread, this method will automatically call itself on a background handler
     * and post the result to the UI thread on the given uiThreadCallback.
     *
     * @param uiThreadCallback
     */
    public static void loadSavedThemeModeAsync(OnThemeLoadedUIThreadCallback uiThreadCallback) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> loadSavedThemeModeAsync(uiThreadCallback));
            return;
        }

        SystemUtils.ensureBackgroundThreadOrCrash("ThemeManager::loadSavedThemeModeAsync");
        String themeEntry = ConfigurationManager.instance().getString(Constants.PREF_KEY_GUI_THEME_MODE, "system");
        if (themeEntry == null) {
            themeEntry = "system";
            ConfigurationManager.instance().setString(Constants.PREF_KEY_GUI_THEME_MODE, themeEntry);
        }
        final int themeMode = getThemeModeFromEntry(themeEntry);
        SystemUtils.postToUIThreadAtFront(() -> uiThreadCallback.onThemeLoaded(themeMode));
    }

    public static void applyThemeMode(int themeMode) {
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }

    public static void applyThemeMode(String themeEntry) {
        int themeMode = getThemeModeFromEntry(themeEntry);
        applyThemeMode(themeMode);
    }

    public static void saveThemeModeAsync(@NonNull String themeEntry) {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> saveThemeModeAsync(themeEntry));
            return;
        }
        SystemUtils.ensureBackgroundThreadOrCrash("ThemeManager::saveThemeModeAsync");
        ConfigurationManager.instance().setString(Constants.PREF_KEY_GUI_THEME_MODE, themeEntry.toLowerCase());
    }

    // Get the theme mode from the theme entry
    public static int getThemeModeFromEntry(@NonNull String themeEntry) {
        initThemeEntryToIntModeMap();
        Integer themeMode = theme_entry_to_int_mode_map.get(themeEntry.toLowerCase());
        if (themeMode == null) {
            themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        return themeMode;
    }

    // Get the theme entry from the theme mode
    public static String getThemeEntryFromMode(int themeMode) {
        initThemeModeToEntryMap();
        return theme_int_mode_to_entry_map.get(themeMode);
    }

    // thread safe lazy initialization of the theme_entry_to_int_mode_map
    private static void initThemeEntryToIntModeMap() {
        if (theme_entry_to_int_mode_map != null) {
            return;
        }
        synchronized (theme_entry_to_int_mode_map_lock) {
            theme_entry_to_int_mode_map = new HashMap<>();
            theme_entry_to_int_mode_map.put("light", AppCompatDelegate.MODE_NIGHT_NO);
            theme_entry_to_int_mode_map.put("dark", AppCompatDelegate.MODE_NIGHT_YES);
            theme_entry_to_int_mode_map.put("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    // thread safe lazy initialization of the theme_mode_to_entry_map
    private static void initThemeModeToEntryMap() {
        if (theme_int_mode_to_entry_map != null) {
            return;
        }
        synchronized (theme_int_mode_to_entry_map_lock) {
            theme_int_mode_to_entry_map = new HashMap<>();
            theme_int_mode_to_entry_map.put(AppCompatDelegate.MODE_NIGHT_NO, "light");
            theme_int_mode_to_entry_map.put(AppCompatDelegate.MODE_NIGHT_YES, "dark");
            theme_int_mode_to_entry_map.put(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, "system");
        }
    }
}
