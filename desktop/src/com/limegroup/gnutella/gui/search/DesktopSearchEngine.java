/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.CommonSearchEngine;
import com.frostwire.search.SearchEngineSettingsAdapter;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.TellurideAdapter;
import com.frostwire.search.archiveorg.ArchiveorgSearchPerformer;
import com.frostwire.search.btdigg.BTDiggSearchPerformer;
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.glotorrents.GloTorrentsSearchPerformer;
import com.frostwire.search.idope.IdopeSearchPerformer;
import com.frostwire.search.knaben.KnabenSearchPerformer;
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import com.frostwire.search.magnetdl.MagnetDLSearchPerformer;
import com.frostwire.search.nyaa.NyaaSearchPerformer;
import com.frostwire.search.one337x.One337xSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.torrentz2.Torrentz2SearchPerformer;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yt.YTSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.OSUtils;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.search.adapters.DesktopSearchEngineSettingsAdapter;
import com.limegroup.gnutella.gui.search.adapters.DesktopTellurideAdapter;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.BooleanSetting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Desktop implementation of SearchEngine using the consolidated CommonSearchEngine
 * with platform-specific adapters.
 * 
 * @author gubatron
 * @author aldenml
 */
public abstract class DesktopSearchEngine extends CommonSearchEngine {

    private static final int DEFAULT_TIMEOUT = 5000;
    
    private static final SearchEngineSettingsAdapter SETTINGS_ADAPTER;
    private static final TellurideAdapter TELLURIDE_ADAPTER = new DesktopTellurideAdapter();

    static {
        // Create settings map
        Map<String, BooleanSetting> settingsMap = new HashMap<>();
        settingsMap.put("BT_DIGG", SearchEnginesSettings.BT_DIGG_SEARCH_ENABLED);
        settingsMap.put("TPB", SearchEnginesSettings.TPB_SEARCH_ENABLED);
        settingsMap.put("SOUNDCLOUD", SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED);
        settingsMap.put("ARCHIVEORG", SearchEnginesSettings.ARCHIVEORG_SEARCH_ENABLED);
        settingsMap.put("FROSTCLICK", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED);
        settingsMap.put("TORLOCK", SearchEnginesSettings.TORLOCK_SEARCH_ENABLED);
        settingsMap.put("TORRENTDOWNLOADS", SearchEnginesSettings.TORRENTDOWNLOADS_SEARCH_ENABLED);
        settingsMap.put("LIMETORRENTS", SearchEnginesSettings.LIMETORRENTS_SEARCH_ENABLED);
        settingsMap.put("ONE337X", SearchEnginesSettings.ONE337X_SEARCH_ENABLED);
        settingsMap.put("IDOPE", SearchEnginesSettings.IDOPE_SEARCH_ENABLED);
        settingsMap.put("NYAA", SearchEnginesSettings.NYAA_SEARCH_ENABLED);
        settingsMap.put("TORRENTZ2", SearchEnginesSettings.TORRENTZ2_SEARCH_ENABLED);
        settingsMap.put("MAGNETDL", SearchEnginesSettings.MAGNETDL_ENABLED);
        settingsMap.put("GLOTORRENTS", SearchEnginesSettings.GLOTORRENTS_ENABLED);
        settingsMap.put("TELLURIDE", SearchEnginesSettings.TELLURIDE_ENABLED);
        settingsMap.put("YT", SearchEnginesSettings.YT_SEARCH_ENABLED);
        settingsMap.put("TORRENTSCSV", SearchEnginesSettings.TORRENTSCSV_SEARCH_ENABLED);
        settingsMap.put("KNABEN", SearchEnginesSettings.KNABEN_SEARCH_ENABLED);
        
        SETTINGS_ADAPTER = new DesktopSearchEngineSettingsAdapter(settingsMap);
    }

    protected DesktopSearchEngine(String name, String preferenceKey, String domainName) {
        super(name, preferenceKey, domainName, SETTINGS_ADAPTER, TELLURIDE_ADAPTER);
    }

    // Concrete implementations
    private static final DesktopSearchEngine BT_DIGG = new DesktopSearchEngine("BTDigg", "BT_DIGG", "btdig.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BTDiggSearchPerformer(token, keywords, DEFAULT_TIMEOUT * 6);
        }
    };

    private static final DesktopSearchEngine TPB = new DesktopSearchEngine("TPB", "TPB", null) {
        private String domainName = null;
        
        @Override
        protected void postInitWork() {
            // while this is happening TPB.isReady() should be false, as it's initialized with a null domain name.
            new Thread(() -> {
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                // from https://piratebayproxy.info/
                domainName = UrlUtils.getFastestMirrorDomain(httpClient, TPBSearchPerformer.getMirrors(), 1000, 6);
            }).start();
        }

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            if (!isReady()) {
                throw new RuntimeException("Check your logic, a search performer that's not ready should not be in the list of performers yet.");
            }
            return new TPBSearchPerformer(domainName, token, keywords, DEFAULT_TIMEOUT);
        }
        
        @Override
        protected boolean isReady() {
            return domainName != null;
        }
        
        @Override
        public String getDomainName() {
            return domainName;
        }
    };

    private static final DesktopSearchEngine SOUNDCLOUD = new DesktopSearchEngine("Soundcloud", "SOUNDCLOUD", "api-v2.soundcloud.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine ARCHIVEORG = new DesktopSearchEngine("Archive.org", "ARCHIVEORG", "archive.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ArchiveorgSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine FROSTCLICK = new DesktopSearchEngine("FrostClick", "FROSTCLICK", "api.frostclick.com") {
        private final UserAgent userAgent = new UserAgent(OSUtils.getFullOS(), FrostWireUtils.getFrostWireVersion(), String.valueOf(FrostWireUtils.getBuildNumber()));

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT, userAgent);
        }
    };

    private static final DesktopSearchEngine TORLOCK = new DesktopSearchEngine("TorLock", "TORLOCK", "www.torlock.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorLockSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine TORRENTDOWNLOADS = new DesktopSearchEngine("TorrentDownloads", "TORRENTDOWNLOADS", "www.torrentdownloads.me") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentDownloadsSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine LIMETORRENTS = new DesktopSearchEngine("LimeTorrents", "LIMETORRENTS", "www.limetorrents.info") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new LimeTorrentsSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine NYAA = new DesktopSearchEngine("Nyaa", "NYAA", "nyaa.si") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new NyaaSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine ONE337X = new DesktopSearchEngine("1337x", "ONE337X", "www.1377x.to") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new One337xSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine IDOPE = new DesktopSearchEngine("Idope", "IDOPE", "idope.se") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new IdopeSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine TORRENTZ2 = new DesktopSearchEngine("Torrentz2", "TORRENTZ2", "torrentz2.eu") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new Torrentz2SearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine MAGNETDL = new DesktopSearchEngine("MagnetDL", "MAGNETDL", "magnetdl.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MagnetDLSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine GLOTORRENTS = new DesktopSearchEngine("GloTorrents", "GLOTORRENTS", "gtdb.to") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new GloTorrentsSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine TELLURIDE = new DesktopSearchEngine("Cloud Backup", "TELLURIDE", "*") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TellurideSearchPerformer(token,
                    keywords,
                    new TellurideSearchPerformerDesktopListener(),
                    FrostWireUtils.getTellurideLauncherFile());
        }
    };

    private static final DesktopSearchEngine YT = new DesktopSearchEngine("YT", "YT", "www.youtube.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YTSearchPerformer(token, keywords, DEFAULT_TIMEOUT, 1);
        }
    };

    private static final DesktopSearchEngine TORRENTSCSV = new DesktopSearchEngine("TorrentsCSV", "TORRENTSCSV", "torrents-csv.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentsCSVSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final DesktopSearchEngine KNABEN = new DesktopSearchEngine("Knaben", "KNABEN", "knaben.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new KnabenSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    // List of all engines
    public static List<DesktopSearchEngine> getEngines() {
        return Arrays.asList(
                YT,
                BT_DIGG,
                ARCHIVEORG,
                GLOTORRENTS,
                IDOPE,
                KNABEN,
                LIMETORRENTS,
                MAGNETDL,
                NYAA,
                ONE337X,
                TPB,
                TORLOCK,
                TORRENTDOWNLOADS,
                TORRENTZ2,
                TORRENTSCSV,
                SOUNDCLOUD,
                FROSTCLICK);
    }

    public static DesktopSearchEngine getSearchEngineByName(String name) {
        if (name.startsWith("Cloud:")) {
            return TELLURIDE;
        }
        return getEngines().stream()
                .filter(se -> name.startsWith(se.getName()))
                .findFirst()
                .orElse(null);
    }
}