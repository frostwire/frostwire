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

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Looking for default config values? look at {@link ConfigurationDefaults}
 *
 * @author gubatron
 * @author aldenml
 */
public class ConfigurationManager {
    private static final Logger LOG = Logger.getLogger(ConfigurationManager.class);
    private static ConfigurationManager instance;
    private static AtomicReference state = new AtomicReference(State.CREATING);
    private final static CountDownLatch creationLatch = new CountDownLatch(1);
    private SharedPreferences preferences;
    private Editor editor;
    private ConfigurationDefaults defaults;

    private enum State {
        CREATING,
        CREATED
    }

    public synchronized static void create(Application context) {
        if (State.CREATED == state.get()  && instance != null) {
            return;
        }
        instance = new ConfigurationManager(context);
    }

    public static ConfigurationManager instance() {
        if (state.get() == State.CREATING) {
            try {
                creationLatch.await();
            } catch (InterruptedException e) {
                if (instance == null) {
                    throw new RuntimeException("ConfigurationManager not created, creationLatch thread interrupted");
                }
            }
        }
        if (State.CREATED == state.get() && instance == null) {
            throw new RuntimeException("ConfigurationManager not created");
        }
        return instance;
    }

    private ConfigurationManager(Application context) {
        Engine.instance().getThreadPool().execute(new Initializer(this, context));
    }

    private static class Initializer implements Runnable {
        private final ConfigurationManager cm;
        private final Application applicationRef;
        Initializer(ConfigurationManager configurationManager, Application application) {
            cm = configurationManager;
            applicationRef = application;
        }

        @Override
        public void run() {
            try {
                cm.preferences = PreferenceManager.getDefaultSharedPreferences(applicationRef);
                cm.editor = cm.preferences.edit();
                cm.defaults = new ConfigurationDefaults();

                if (cm.editor != null) {
                    cm.initPreferences();
                }
            }
            catch (Throwable t) {
                LOG.error("Error initializing ConfigurationManager", t);
                throw t;
            } finally {
                state.set(State.CREATED);
                creationLatch.countDown();
            }
        }
    }

    private void applyInBackground() {
        if (editor == null) {
            LOG.warn("applyInBackground aborted, editor == null");
            return;
        }
        editor.apply();
    }

    public String getString(String key, String defValue) {
        if (preferences == null) {
            LOG.error("getString(key=" + key + ", defValue=" + defValue + ") preferences == null");
            throw new IllegalStateException("getString(key="+key+") failed, preferences:SharedPreferences is null");
        }
        return preferences.getString(key, defValue);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public void setString(String key, String value) {
        try {
            editor.putString(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setString(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public int getInt(String key, int defValue) {
        if (preferences == null) {
            LOG.error("getInt(key=" + key + ", defValue=" + defValue + ") preferences == null");
            throw new IllegalStateException("getInt(key="+key+") failed, preferences:SharedPreferences is null");
        }
        return preferences.getInt(key, defValue);
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public void setInt(String key, int value) {
        try {
            editor.putInt(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setInt(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public long getLong(String key, long defValue) {
        if (preferences == null) {
            LOG.error("getLong(key=" + key + ", defValue=" + defValue + ") preferences == null");
            throw new IllegalStateException("getLong(key="+key+") failed, preferences:SharedPreferences is null");
        }
        return preferences.getLong(key, defValue);
    }

    public long getLong(String key) {
        return getLong(key, 0);
    }

    public void setLong(String key, long value) {
        try {
            editor.putLong(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setLong(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public boolean getBoolean(String key) {
        if (preferences == null) {
            LOG.error("getBoolean(key=" + key + ") preferences == null");
            throw new IllegalStateException("getBoolean(key="+key+") failed, preferences:SharedPreferences is null");
        }
        return preferences.getBoolean(key, false);
    }

    public void setBoolean(String key, boolean value) {
        try {
            editor.putBoolean(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setBoolean(key=" + key + ", value=" + value + ") failed", ignore);
        }
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
        String s = getString(key);
        return s != null ? JsonUtils.toObject(s, String[].class) : null;
    }


    public void setStringArray(String key, String[] values) {
        setString(key, JsonUtils.toJson(values));
    }

    public boolean showTransfersOnDownloadStart() {
        return getBoolean(Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START);
    }

    public void registerOnPreferenceChange(OnSharedPreferenceChangeListener listener) {
        if (preferences != null) {
            preferences.registerOnSharedPreferenceChangeListener(listener);
        }
    }

    public void unregisterOnPreferenceChange(OnSharedPreferenceChangeListener listener) {
        if (preferences != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener);
        }
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
        } else if (value instanceof String[]) {
            initStringArrayPreference(key, (String[]) value, force);
        } else {
            throw new RuntimeException("Unsupported data type for setting: " +
                    "key = " + key + ", value = " + value.getClass());
        }
    }

    private void initStringPreference(String prefKeyName, String defaultValue, boolean force) {
        if (preferences == null && !force) {
            LOG.warn("initStringPreference(prefKeyName="+prefKeyName+", defaultValue="+defaultValue+") aborted, preferences == null");
            return;
        }
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
            setString(prefKeyName, defaultValue);
        }
    }

    private void initBooleanPreference(String prefKeyName, boolean defaultValue, boolean force) {
        if (preferences == null && !force) {
            LOG.warn("initBooleanPreference(prefKeyName="+prefKeyName+", defaultValue="+defaultValue+") aborted, preferences == null");
            return;
        }
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
            setBoolean(prefKeyName, defaultValue);
        }
    }

    private void initIntPreference(String prefKeyName, int defaultValue, boolean force) {
        if (preferences == null && !force) {
            LOG.warn("initIntPreference(prefKeyName="+prefKeyName+", defaultValue="+defaultValue+") aborted, preferences == null");
            return;
        }
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
            setInt(prefKeyName, defaultValue);
        }
    }

    private void initLongPreference(String prefKeyName, long defaultValue, boolean force) {
        if (preferences == null && !force) {
            LOG.warn("initLongPreference(prefKeyName="+prefKeyName+", defaultValue="+defaultValue+") aborted, preferences == null");
            return;
        }
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
            setLong(prefKeyName, defaultValue);
        }
    }

    private void initStringArrayPreference(String prefKeyName, String[] defaultValue, boolean force) {
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
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
            } else if (entry.getValue() instanceof String[]) {
                setStringArray(entry.getKey(), (String[]) entry.getValue());
            } else {
                Object value = entry.getValue();
                throw new RuntimeException("Unsupported data type for setting: " +
                        "key = " + entry.getKey() + ", value = " +
                        (value != null ? value.getClass() : "null"));
            }
        }
    }
}
