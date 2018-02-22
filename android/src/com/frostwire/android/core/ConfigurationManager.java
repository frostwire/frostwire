/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.core;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import com.frostwire.util.Hex;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.StringUtils;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Looking for default config values? look at {@link ConfigurationDefaults}
 *
 * @author gubatron
 * @author aldenml
 */
public class ConfigurationManager {
    private final SharedPreferences preferences;
    private final Editor editor;

    private final ConfigurationDefaults defaults;

    private static ConfigurationManager instance;

    public synchronized static void create(Application context) {
        if (instance != null) {
            return;
        }
        instance = new ConfigurationManager(context);
    }

    public static ConfigurationManager instance() {
        if (instance == null) {
            throw new RuntimeException("ConfigurationManager not created");
        }
        return instance;
    }

    private ConfigurationManager(Application context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        editor = preferences.edit();
        defaults = new ConfigurationDefaults();
        initPreferences();
        migrateWifiOnlyPreference();
    }

    /**
     * If the deprecated {@link Constants#PREF_KEY_NETWORK_USE_WIFI_ONLY} is found
     * it gets migrated to the new {@link Constants#PREF_KEY_NETWORK_USE_WIFI_ONLY and then deleted.
     */
    private void migrateWifiOnlyPreference() {
        if (!preferences.contains(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA)) {
            return;
        }
        setBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY, !getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA));
        removePreference(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
    }

    private void removePreference(String key) {
        try {
            editor.remove(key);
            editor.apply();
        } catch (Throwable ignore) {
        }
    }

    public String getString(String key) {
        return preferences.getString(key, null);
    }

    public void setString(String key, String value) {
        editor.putString(key, value);
        editor.apply();
    }

    public int getInt(String key) {
        return preferences.getInt(key, 0);
    }

    public void setInt(String key, int value) {
        editor.putInt(key, value);
        editor.apply();
    }

    public long getLong(String key) {
        return preferences.getLong(key, 0);
    }

    public void setLong(String key, long value) {
        editor.putLong(key, value);
        editor.apply();
    }

    public boolean getBoolean(String key) {
        return preferences.getBoolean(key, false);
    }

    public void setBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    public File getFile(String key) {
        return new File(preferences.getString(key, ""));
    }

    private void setFile(String key, File value) {
        editor.putString(key, value.getAbsolutePath());
        editor.apply();
    }

    public byte[] getByteArray(String key) {
        String str = getString(key);

        if (StringUtils.isNullOrEmpty(str)) {
            return null;
        }

        try {
            return Hex.decode(str);
        } catch (Throwable e) {
            return null;
        }
    }

    private void setByteArray(String key, byte[] value) {
        setString(key, Hex.encode(value));
    }

    public void resetToDefaults() {
        resetToDefaults(defaults.getDefaultValues());
    }

    private void resetToDefault(String key) {
        if (defaults != null) {
            Map<String, Object> defaultValues = defaults.getDefaultValues();
            if (defaultValues != null && defaultValues.containsKey(key)) {
                Object defaultValue = defaultValues.get(key);
                initPreference(key, defaultValue, true);
            }
        }
    }

    public String getUUIDString() {
        return getString(Constants.PREF_KEY_CORE_UUID);
    }

    public int getLastMediaTypeFilter() {
        return getInt(Constants.PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER);
    }

    public void setLastMediaTypeFilter(int mediaTypeId) {
        setInt(Constants.PREF_KEY_GUI_LAST_MEDIA_TYPE_FILTER, mediaTypeId);
    }

    public boolean vibrateOnFinishedDownload() {
        return getBoolean(Constants.PREF_KEY_GUI_VIBRATE_ON_FINISHED_DOWNLOAD);
    }

    public String[] getStringArray(String key) {
        String jsonStringArray = preferences.getString(key, null);
        if (jsonStringArray == null) {
            return null;
        }
        return JsonUtils.toObject(jsonStringArray, String[].class);
    }


    public void setStringArray(String key, String[] values) {
        editor.putString(key, JsonUtils.toJson(values));
        editor.apply();
    }

    public boolean showTransfersOnDownloadStart() {
        return getBoolean(Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START);
    }

    public void registerOnPreferenceChange(OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterOnPreferenceChange(OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public String getStoragePath() {
        return getString(Constants.PREF_KEY_STORAGE_PATH);
    }

    public void setStoragePath(String path) {
        if (path != null && path.length() > 0) { // minor verifications
            setString(Constants.PREF_KEY_STORAGE_PATH, path);
        }
    }

    public boolean isSeedFinishedTorrents() {
        return getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);
    }

    public void setSeedFinishedTorrents(boolean value) {
        setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, value);
    }

    public boolean isSeedingEnabledOnlyForWifi() {
        return getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);
    }

    private void initPreferences() {
        for (Entry<String, Object> entry : defaults.getDefaultValues().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            initPreference(key, value, false);
        }

        //there are some configuration values that need to be reset every time to a desired value
        resetToDefaults(defaults.getResetValues());
    }

    private void initPreference(String key, Object value, boolean force) {
        if (value instanceof String) {
            initStringPreference(key, (String) value, force);
        } else if (value instanceof Integer) {
            initIntPreference(key, (Integer) value, force);
        } else if (value instanceof Long) {
            initLongPreference(key, (Long) value, force);
        } else if (value instanceof Boolean) {
            initBooleanPreference(key, (Boolean) value, force);
        } else if (value instanceof byte[]) {
            initByteArrayPreference(key, (byte[]) value, force);
        } else if (value instanceof File) {
            initFilePreference(key, (File) value, force);
        } else if (value instanceof String[]) {
            initStringArrayPreference(key, (String[]) value, force);
        }
    }

    private void initStringPreference(String prefKeyName, String defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setString(prefKeyName, defaultValue);
        }
    }

    private void initByteArrayPreference(String prefKeyName, byte[] defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setByteArray(prefKeyName, defaultValue);
        }
    }

    private void initBooleanPreference(String prefKeyName, boolean defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setBoolean(prefKeyName, defaultValue);
        }
    }

    private void initIntPreference(String prefKeyName, int defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setInt(prefKeyName, defaultValue);
        }
    }

    private void initLongPreference(String prefKeyName, long defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setLong(prefKeyName, defaultValue);
        }
    }

    private void initFilePreference(String prefKeyName, File defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setFile(prefKeyName, defaultValue);
        }
    }

    private void initStringArrayPreference(String prefKeyName, String[] defaultValue, boolean force) {
        if (!preferences.contains(prefKeyName) || force) {
            setStringArray(prefKeyName, defaultValue);
        }
    }

    private void resetToDefaults(Map<String, Object> map) {
        for (Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof String) {
                setString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                setInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                setLong(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                setBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof byte[]) {
                setByteArray(entry.getKey(), (byte[]) entry.getValue());
            } else if (entry.getValue() instanceof File) {
                setFile(entry.getKey(), (File) entry.getValue());
            } else if (entry.getValue() instanceof String[]) {
                setStringArray(entry.getKey(), (String[]) entry.getValue());
            }
        }
    }

}
