/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import android.os.Build;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.search.engines.SearchEngineAdapter;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.TellurideSearchFactory;
import com.frostwire.search.frostclick.UserAgent;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Android's side of the shared search engines (frostwire/frostwire#1002): each engine's on/off
 * setting is the preference Android has always saved, the Local test engine is offered in debug
 * builds only, YT is hidden on Google Play, and FrostWire's servers can switch an engine off while
 * the app runs ({@link #setActive}).
 */
public final class AndroidSearchEngineAdapter implements SearchEngineAdapter {

  private static final Map<SearchEngineID, String> PREFERENCE_KEYS =
      new EnumMap<>(SearchEngineID.class);

  static {
    PREFERENCE_KEYS.put(SearchEngineID.TPB_ID, Constants.PREF_KEY_SEARCH_USE_TPB);
    PREFERENCE_KEYS.put(SearchEngineID.SOUNDCLOUD_ID, Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD);
    PREFERENCE_KEYS.put(
        SearchEngineID.INTERNET_ARCHIVE_ID, Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG);
    PREFERENCE_KEYS.put(SearchEngineID.FROSTCLICK_ID, Constants.PREF_KEY_SEARCH_USE_FROSTCLICK);
    PREFERENCE_KEYS.put(SearchEngineID.ONE337X_ID, Constants.PREF_KEY_SEARCH_USE_ONE337X);
    PREFERENCE_KEYS.put(SearchEngineID.NYAA_ID, Constants.PREF_KEY_SEARCH_USE_NYAA);
    PREFERENCE_KEYS.put(SearchEngineID.TORRENTZ2_ID, Constants.PREF_KEY_SEARCH_USE_TORRENTZ2);
    PREFERENCE_KEYS.put(SearchEngineID.MAGNETDL_ID, Constants.PREF_KEY_SEARCH_USE_MAGNETDL);
    PREFERENCE_KEYS.put(
        SearchEngineID.TELLURIDE_ID, Constants.PREF_KEY_SEARCH_USE_TELLURIDE_COURIER);
    PREFERENCE_KEYS.put(SearchEngineID.YT_ID, Constants.PREF_KEY_SEARCH_USE_YT);
    PREFERENCE_KEYS.put(SearchEngineID.TORRENTSCSV_ID, Constants.PREF_KEY_SEARCH_USE_TORRENTSCSV);
    PREFERENCE_KEYS.put(SearchEngineID.KNABEN_ID, Constants.PREF_KEY_SEARCH_USE_KNABEN);
    PREFERENCE_KEYS.put(SearchEngineID.BITSEARCH_ID, Constants.PREF_KEY_SEARCH_USE_BITSEARCH);
    PREFERENCE_KEYS.put(SearchEngineID.LOCAL_ID, Constants.PREF_KEY_SEARCH_USE_LOCAL);
    PREFERENCE_KEYS.put(SearchEngineID.DISTRIBUTED_ID, Constants.PREF_KEY_SEARCH_USE_DISTRIBUTED);
  }

  private final UserAgent userAgent =
      new UserAgent(
          osVersionString(), Constants.FROSTWIRE_VERSION_STRING, Constants.FROSTWIRE_BUILD);

  /** Engines FrostWire's servers (or the Google Play rules) switched off while the app runs. */
  private final Set<SearchEngineID> inactive = ConcurrentHashMap.newKeySet();

  private volatile TellurideSearchFactory telluride;

  /** The preference that stores whether the engine is on. */
  public static String preferenceKey(SearchEngineID id) {
    String key = PREFERENCE_KEYS.get(id);
    if (key == null) {
      throw new IllegalArgumentException("No preference for " + id);
    }
    return key;
  }

  static String osVersionString() {
    return Build.VERSION.CODENAME
        + "_"
        + Build.VERSION.INCREMENTAL
        + "_"
        + Build.VERSION.RELEASE
        + "_"
        + Build.VERSION.SDK_INT;
  }

  @Override
  public boolean isEnabled(SearchEngineID id) {
    return ConfigurationManager.instance().getBoolean(preferenceKey(id));
  }

  @Override
  public void setEnabled(SearchEngineID id, boolean enabled) {
    ConfigurationManager.instance().setBoolean(preferenceKey(id), enabled);
  }

  @Override
  public boolean isOffered(SearchEngineID id) {
    if (inactive.contains(id)) {
      return false;
    }
    return switch (id) {
      case LOCAL_ID -> BuildConfig.DEBUG;
      case YT_ID -> !Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG;
      default -> true;
    };
  }

  /** Switches an engine off (or back on) for this run, as FrostWire's servers ask. */
  public void setActive(SearchEngineID id, boolean active) {
    if (active) {
      inactive.remove(id);
    } else {
      inactive.add(id);
    }
  }

  @Override
  public UserAgent userAgent() {
    return userAgent;
  }

  @Override
  public String soundCloudClientId() {
    return SoftwareUpdater.getSoundCloudClientId();
  }

  @Override
  public String soundCloudAppVersion() {
    return SoftwareUpdater.getSoundCloudAppVersion();
  }

  @Override
  public TellurideSearchFactory telluride() {
    return telluride;
  }

  /** How searches reach Telluride (the embedded Python runtime); set once it can. */
  public void telluride(TellurideSearchFactory factory) {
    this.telluride = factory;
  }
}
