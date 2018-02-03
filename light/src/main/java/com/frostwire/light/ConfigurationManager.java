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


package com.frostwire.light;

import com.frostwire.util.JsonUtils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.prefs.Preferences;

public final class ConfigurationManager {
    private final ConfigurationDefaults defaults;
    private final Preferences preferences;
    private final LinkedHashSet<String> initializedKeys;

    private static class Loader {
        static final ConfigurationManager instance = new ConfigurationManager();
    }

    private ConfigurationManager() {
        defaults = new ConfigurationDefaults();
        preferences = Preferences.userRoot().node("com.frostwire.light");
        initializedKeys = new LinkedHashSet<>();
        initPreferences();
    }

    public static ConfigurationManager instance() {
        if (Loader.instance == null) {
            throw new RuntimeException("ConfigurationManager not created");
        }
        return Loader.instance;
    }

    private void removePreference(String key) {
        try {
            preferences.remove(key);
        } catch (Throwable ignore) {
        }
    }

    public String getString(String key) {
        return preferences.get(key, null);
    }

    public void setString(String key, String value) {
        preferences.put(key, value);
    }

    public int getInt(String key) {
        return preferences.getInt(key, 0);
    }

    public void setInt(String key, int value) {
        preferences.putInt(key, value);
    }

    public long getLong(String key) {
        return preferences.getLong(key, 0);
    }

    public void setLong(String key, long value) {
        preferences.putLong(key, value);
    }

    public boolean getBoolean(String key) {
        return preferences.getBoolean(key, false);
    }

    public void setBoolean(String key, boolean value) {
        preferences.putBoolean(key, value);
    }

    public File getFile(String key) {
        return new File(preferences.get(key, ""));
    }

    private void setFile(String key, File value) {
        preferences.put(key, value.getAbsolutePath());
    }

    public byte[] getByteArray(String key) {
        return preferences.getByteArray(key, null);
    }

    private void setByteArray(String key, byte[] value) {
        preferences.putByteArray(key, value);
    }

    public String[] getStringArray(String key) {
        String jsonStringArray = preferences.get(key, null);
        if (jsonStringArray == null) {
            return null;
        }
        return JsonUtils.toObject(jsonStringArray, String[].class);
    }

    public void setStringArray(String key, String[] values) {
        preferences.put(key, JsonUtils.toJson(values));
    }

    private void initPreferences() {
        for (Map.Entry<String, Object> entry : defaults.getDefaultValues().entrySet()) {
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
        if (!initializedKeys.contains(prefKeyName) || force) {
            initializedKeys.add(prefKeyName);
            setString(prefKeyName, defaultValue);
        }
    }

    private void initByteArrayPreference(String prefKeyName, byte[] defaultValue, boolean force) {
        if (!initializedKeys.contains(prefKeyName) || force) {
            initializedKeys.add(prefKeyName);
            setByteArray(prefKeyName, defaultValue);
        }
    }

    private void initBooleanPreference(String prefKeyName, boolean defaultValue, boolean force) {
        if (!initializedKeys.contains(prefKeyName) || force) {
            initializedKeys.add(prefKeyName);
            setBoolean(prefKeyName, defaultValue);
        }
    }

    private void initIntPreference(String prefKeyName, int defaultValue, boolean force) {
        if (!initializedKeys.contains(prefKeyName) || force) {
            initializedKeys.add(prefKeyName);
            setInt(prefKeyName, defaultValue);
        }
    }

    private void initLongPreference(String prefKeyName, long defaultValue, boolean force) {
        if (!initializedKeys.contains(prefKeyName) || force) {
            initializedKeys.add(prefKeyName);
            setLong(prefKeyName, defaultValue);
        }
    }

    private void initFilePreference(String prefKeyName, File defaultValue, boolean force) {
        if (!initializedKeys.contains(prefKeyName)|| force) {
            initializedKeys.add(prefKeyName);
            setFile(prefKeyName, defaultValue);
        }
    }

    private void initStringArrayPreference(String prefKeyName, String[] defaultValue, boolean force) {
        if (!initializedKeys.contains(prefKeyName) || force) {
            initializedKeys.add(prefKeyName);
            setStringArray(prefKeyName, defaultValue);
        }
    }



    private void resetToDefaults(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
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
