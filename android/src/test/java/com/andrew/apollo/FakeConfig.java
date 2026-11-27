/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.core.ConfigurationManager;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * A ConfigurationManager that remembers what it is given in a map, for the code that reads and
 * writes preferences. {@link #install} also drops the PreferenceUtils singleton, which holds on to
 * the manager it first saw.
 */
public final class FakeConfig {

  public final Map<String, Object> values = new HashMap<>();

  public static FakeConfig install() throws Exception {
    FakeConfig config = new FakeConfig();
    ConfigurationManager cm = Mockito.mock(ConfigurationManager.class);
    Mockito.when(cm.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenAnswer(
            i -> config.values.getOrDefault(i.<String>getArgument(0), i.<String>getArgument(1)));
    Mockito.when(cm.getString(ArgumentMatchers.anyString()))
        .thenAnswer(i -> (String) config.values.get(i.<String>getArgument(0)));
    Mockito.when(cm.getInt(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
        .thenAnswer(
            i -> config.values.getOrDefault(i.<String>getArgument(0), i.<Integer>getArgument(1)));
    Mockito.when(cm.getInt(ArgumentMatchers.anyString()))
        .thenAnswer(i -> config.values.getOrDefault(i.<String>getArgument(0), 0));
    Mockito.when(cm.getBoolean(ArgumentMatchers.anyString()))
        .thenAnswer(i -> config.values.getOrDefault(i.<String>getArgument(0), false));
    Mockito.when(cm.getLong(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
        .thenAnswer(
            i -> config.values.getOrDefault(i.<String>getArgument(0), i.<Long>getArgument(1)));
    Mockito.doAnswer(
            i -> {
              config.values.put(i.<String>getArgument(0), i.getArgument(1));
              return null;
            })
        .when(cm)
        .setString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    Mockito.doAnswer(
            i -> {
              config.values.put(i.<String>getArgument(0), i.getArgument(1));
              return null;
            })
        .when(cm)
        .setInt(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());
    Mockito.doAnswer(
            i -> {
              config.values.put(i.<String>getArgument(0), i.getArgument(1));
              return null;
            })
        .when(cm)
        .setBoolean(ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean());
    Field instance = ConfigurationManager.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, cm);
    Field prefs = PreferenceUtils.class.getDeclaredField("sInstance");
    prefs.setAccessible(true);
    prefs.set(null, null);
    return config;
  }

  public static void uninstall() throws Exception {
    Field instance = ConfigurationManager.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, null);
    Field prefs = PreferenceUtils.class.getDeclaredField("sInstance");
    prefs.setAccessible(true);
    prefs.set(null, null);
  }
}
