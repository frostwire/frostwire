/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.BuildConfig;
import com.frostwire.gui.updates.SoundCloudConfigFetcher;
import com.frostwire.search.engines.SearchEngineAdapter;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.TellurideSearchFactory;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.util.EnumMap;
import java.util.Map;
import org.limewire.setting.BooleanSetting;

/**
 * Desktop's side of the shared search engines (frostwire/frostwire#1002): each engine's on/off
 * setting is the {@link SearchEnginesSettings} entry desktop has always saved, the Local test
 * engine is offered in debug builds only, and Telluride runs through its launcher.
 */
public final class DesktopSearchEngineAdapter implements SearchEngineAdapter {

  private static final Map<SearchEngineID, BooleanSetting> SETTINGS =
      new EnumMap<>(SearchEngineID.class);

  static {
    SETTINGS.put(SearchEngineID.TPB_ID, SearchEnginesSettings.TPB_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.SOUNDCLOUD_ID, SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED);
    SETTINGS.put(
        SearchEngineID.INTERNET_ARCHIVE_ID, SearchEnginesSettings.INTERNET_ARCHIVE_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.FROSTCLICK_ID, SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.ONE337X_ID, SearchEnginesSettings.ONE337X_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.NYAA_ID, SearchEnginesSettings.NYAA_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.TORRENTZ2_ID, SearchEnginesSettings.TORRENTZ2_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.MAGNETDL_ID, SearchEnginesSettings.MAGNETDL_ENABLED);
    SETTINGS.put(SearchEngineID.TELLURIDE_ID, SearchEnginesSettings.TELLURIDE_ENABLED);
    SETTINGS.put(SearchEngineID.YT_ID, SearchEnginesSettings.YT_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.TORRENTSCSV_ID, SearchEnginesSettings.TORRENTSCSV_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.KNABEN_ID, SearchEnginesSettings.KNABEN_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.BITSEARCH_ID, SearchEnginesSettings.BITSEARCH_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.LOCAL_ID, SearchEnginesSettings.LOCAL_SEARCH_ENABLED);
    SETTINGS.put(SearchEngineID.DISTRIBUTED_ID, SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED);
  }

  private final UserAgent userAgent =
      new UserAgent(
          OSUtils.getFullOS(),
          FrostWireUtils.getFrostWireVersion(),
          String.valueOf(FrostWireUtils.getBuildNumber()));

  private final TellurideSearchFactory telluride =
      (token, url, playlistMode) ->
          new TellurideSearchPerformer(
              token,
              url,
              new TellurideSearchPerformerDesktopListener(),
              FrostWireUtils.getTellurideLauncherFile(),
              playlistMode);

  /** The setting that stores whether the engine is on, for the Options pane and MCP tools. */
  public static BooleanSetting settingFor(SearchEngineID id) {
    BooleanSetting setting = SETTINGS.get(id);
    if (setting == null) {
      throw new IllegalArgumentException("No setting for " + id);
    }
    return setting;
  }

  @Override
  public boolean isEnabled(SearchEngineID id) {
    return settingFor(id).getValue();
  }

  @Override
  public void setEnabled(SearchEngineID id, boolean enabled) {
    settingFor(id).setValue(enabled);
  }

  @Override
  public boolean isOffered(SearchEngineID id) {
    return id != SearchEngineID.LOCAL_ID || BuildConfig.DEBUG;
  }

  @Override
  public UserAgent userAgent() {
    return userAgent;
  }

  @Override
  public String soundCloudClientId() {
    return SoundCloudConfigFetcher.getClientId();
  }

  @Override
  public String soundCloudAppVersion() {
    return SoundCloudConfigFetcher.getAppVersion();
  }

  @Override
  public TellurideSearchFactory telluride() {
    return telluride;
  }
}
