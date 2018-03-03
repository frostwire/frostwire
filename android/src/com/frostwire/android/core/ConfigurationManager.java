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
import android.os.Looper;
import android.preference.PreferenceManager;

import com.frostwire.android.gui.services.Engine;
import com.frostwire.util.Hex;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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
    private final ExecutorService pool;
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
                if (creationLatch.getCount() == 1) {
                    creationLatch.await();
                }
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
        pool = Engine.instance().getThreadPool();
        pool.execute(new Initializer(this, context));
    }

    private static class Initializer implements Runnable {
        private final ConfigurationManager cm;
        private final WeakReference<Application> applicationRef;
        Initializer(ConfigurationManager configurationManager, Application application) {
            cm = configurationManager;
            applicationRef = Ref.weak(application);
        }

        @Override
        public void run() {
            if (!Ref.alive(applicationRef)) {
                throw new RuntimeException("ConfigurationManager.Initializer aborted, no Context available");
            }
            try {
                cm.preferences = PreferenceManager.getDefaultSharedPreferences(applicationRef.get());
                if (cm.preferences != null) {
                    cm.editor = cm.preferences.edit();
                }
                cm.defaults = new ConfigurationDefaults();
                cm.initPreferences();
                cm.migrateWifiOnlyPreference();
            }
            catch (Throwable ignored) {
                LOG.error("Error initializing ConfigurationManager", ignored);
            }
            finally {
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
        if (Looper.getMainLooper() == Looper.myLooper()) {
            pool.execute(() -> editor.apply());
        } else {
            editor.apply();
        }
    }

    /**
     * If the deprecated {@link Constants#PREF_KEY_NETWORK_USE_WIFI_ONLY} is found
     * it gets migrated to the new {@link Constants#PREF_KEY_NETWORK_USE_WIFI_ONLY and then deleted.
     */
    @SuppressWarnings("deprecation")
    private void migrateWifiOnlyPreference() {
        if (preferences != null && !preferences.contains(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA)) {
            return;
        }
        setBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY, !getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA));
        removePreference(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
    }

    private void removePreference(String key) {
        try {
            editor.remove(key);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("removePreference(key=" + key + ") failed", ignore);
        }
    }

    public String getString(String key) {
        return preferences.getString(key, null);
    }

    public void setString(String key, String value) {
        try {
            editor.putString(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setString(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public int getInt(String key) {
        return preferences.getInt(key, 0);
    }

    public void setInt(String key, int value) {
        try {
            editor.putInt(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setInt(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public long getLong(String key) {
        return preferences.getLong(key, 0);
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
        if (preferences != null) {
            return preferences.getBoolean(key, false);
        }
        LOG.warn("getBoolean defaulting to false, preferences == null");
        return false;
    }

    public void setBoolean(String key, boolean value) {
        try {
            editor.putBoolean(key, value);
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setBoolean(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public File getFile(String key) {
        return new File(preferences.getString(key, ""));
    }

    private void setFile(String key, File value) {
        try {
            editor.putString(key, value.getAbsolutePath());
            applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setFile(key=" + key + ", value=" + value.getAbsolutePath() + ") failed", ignore);
        }
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
        try {
        editor.putString(key, JsonUtils.toJson(values));
        applyInBackground();
        } catch (Throwable ignore) {
            LOG.warn("setStringArray(key=" + key + ", values=...) failed", ignore);
        }
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
        } else if (value instanceof byte[]) {
            initByteArrayPreference(key, (byte[]) value, force);
        } else if (value instanceof File) {
            initFilePreference(key, (File) value, force);
        } else if (value instanceof String[]) {
            initStringArrayPreference(key, (String[]) value, force);
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

    private void initByteArrayPreference(String prefKeyName, byte[] defaultValue, boolean force) {
        if (preferences == null && !force) {
            LOG.warn("initByteArrayPreference(prefKeyName="+prefKeyName+", defaultValue=...) aborted, preferences == null");
            return;
        }
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
            setByteArray(prefKeyName, defaultValue);
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

    private void initFilePreference(String prefKeyName, File defaultValue, boolean force) {
        if (preferences == null && !force) {
            LOG.warn("initFilePreference(prefKeyName="+prefKeyName+", defaultValue=...) aborted, preferences == null");
            return;
        }
        if ((preferences != null && !preferences.contains(prefKeyName)) || force) {
            setFile(prefKeyName, defaultValue);
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
