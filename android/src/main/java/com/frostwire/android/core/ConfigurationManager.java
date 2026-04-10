/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.core;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;

import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Central configuration access point. Delegates to {@link ConfigurationRepository}
 * which uses an in-memory ConcurrentHashMap cache backed by Jetpack DataStore.
 *
 * @author gubatron
 * @author aldenml
 */
@SuppressLint("CommitPrefEdits")
public final class ConfigurationManager {
    private static final CountDownLatch creationLatch;
    private static final Logger LOG = Logger.getLogger(ConfigurationManager.class);

    private static ConfigurationManager instance;
    private static Thread creatorThread;

    static {
        creationLatch = new CountDownLatch(1);
    }

    public static void create(@NonNull Context context) {
        if (instance != null) {
            throw new RuntimeException("CHECK YOUR LOGIC: ConfigurationManager.create(ctx) can only be called once.");
        }
        creatorThread = new Thread(() -> {
            ConfigurationRepository.initialize(context);
            instance = new ConfigurationManager();
            creationLatch.countDown();
        });
        creatorThread.setName("ConfigurationManager::creator");
        creatorThread.setPriority(Thread.MAX_PRIORITY);
        creatorThread.start();
    }

    public static ConfigurationManager instance() {
        if (instance == null) {
            try {
                creationLatch.await(4, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (instance == null) {
            waitForCreatorThread();
            if (instance == null) {
                throw new RuntimeException("The ConfigurationManager instance() creation timed out, try reinstalling the app or notify FrostWire developers");
            }
        }
        return instance;
    }

    private static void waitForCreatorThread() {
        try {
            if (creatorThread != null && creatorThread.isAlive()) {
                creatorThread.join(5000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ConfigurationManager() {
    }

    public String getString(String key, String defValue) {
        String result = ConfigurationRepository.getString(key, defValue);
        return result != null ? result : defValue;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public void setString(String key, String value) {
        try {
            ConfigurationRepository.setString(key, value);
        } catch (Throwable ignore) {
            LOG.warn("setString(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public int getInt(String key, int defValue) {
        return ConfigurationRepository.getInt(key, defValue);
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public void setInt(String key, int value) {
        try {
            ConfigurationRepository.setInt(key, value);
        } catch (Throwable ignore) {
            LOG.warn("setInt(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public long getLong(String key, long defValue) {
        return ConfigurationRepository.getLong(key, defValue);
    }

    public long getLong(String key) {
        return getLong(key, 0);
    }

    public void setLong(String key, long value) {
        try {
            ConfigurationRepository.setLong(key, value);
        } catch (Throwable ignore) {
            LOG.warn("setLong(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public boolean getBoolean(String key) {
        return ConfigurationRepository.getBoolean(key, false);
    }

    public void setBoolean(String key, boolean value) {
        try {
            ConfigurationRepository.setBoolean(key, value);
        } catch (Throwable ignore) {
            LOG.warn("setBoolean(key=" + key + ", value=" + value + ") failed", ignore);
        }
    }

    public void resetToDefaults() {
        ConfigurationRepository.resetToDefaults();
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
        return ConfigurationRepository.getStringArray(key);
    }

    public void setStringArray(String key, String[] values) {
        try {
            ConfigurationRepository.setStringArray(key, values);
        } catch (Throwable ignore) {
            LOG.warn("setStringArray(key=" + key + ", value=" + Arrays.toString(values) + ") failed", ignore);
        }
    }

    public boolean showTransfersOnDownloadStart() {
        return getBoolean(Constants.PREF_KEY_GUI_SHOW_TRANSFERS_ON_DOWNLOAD_START);
    }

    public void registerOnPreferenceChange(ConfigurationRepository.OnPreferenceChangeListener listener) {
        ConfigurationRepository.addListener(listener);
    }

    public void unregisterOnPreferenceChange(ConfigurationRepository.OnPreferenceChangeListener listener) {
        ConfigurationRepository.removeListener(listener);
    }

    public String getStoragePath() {
        return getString(Constants.PREF_KEY_STORAGE_PATH);
    }

    public void setStoragePath(String path) {
        if (path != null && path.length() > 0) {
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
}
