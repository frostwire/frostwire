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

package com.frostwire.android.gui;

import android.os.Build;

import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.adapters.AndroidSearchEngineSettingsAdapter;
import com.frostwire.android.gui.adapters.AndroidTellurideAdapter;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
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
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import com.frostwire.search.magnetdl.MagnetDLSearchPerformer;
import com.frostwire.search.nyaa.NyaaSearchPerformer;
import com.frostwire.search.one337x.One337xSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.torrentz2.Torrentz2SearchPerformer;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yt.YTSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;

import java.util.Arrays;
import java.util.List;

/**
 * Android implementation of SearchEngine using the consolidated CommonSearchEngine
 * with platform-specific adapters.
 * 
 * @author gubatron
 * @author aldenml
 */
public abstract class AndroidSearchEngine extends CommonSearchEngine {

    private static final UserAgent FROSTWIRE_ANDROID_USER_AGENT = new UserAgent(getOSVersionString(), Constants.FROSTWIRE_VERSION_STRING, Constants.FROSTWIRE_BUILD);
    private static final int DEFAULT_TIMEOUT = 5000;
    
    private static final SearchEngineSettingsAdapter SETTINGS_ADAPTER = new AndroidSearchEngineSettingsAdapter();
    private static final TellurideAdapter TELLURIDE_ADAPTER = new AndroidTellurideAdapter();

    protected AndroidSearchEngine(String name, String preferenceKey, String domainName) {
        super(name, preferenceKey, domainName, SETTINGS_ADAPTER, TELLURIDE_ADAPTER);
    }

    static String getOSVersionString() {
        return Build.VERSION.CODENAME + "_" + Build.VERSION.INCREMENTAL + "_" + Build.VERSION.RELEASE + "_" + Build.VERSION.SDK_INT;
    }

    // Concrete implementations
    public static final AndroidSearchEngine SOUNDCLOUD = new AndroidSearchEngine("Soundcloud", Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD, "api-v2.sndcdn.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine ARCHIVE = new AndroidSearchEngine("Archive.org", Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG, "archive.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ArchiveorgSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine FROSTCLICK = new AndroidSearchEngine("FrostClick", Constants.PREF_KEY_SEARCH_USE_FROSTCLICK, "api.frostclick.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT, FROSTWIRE_ANDROID_USER_AGENT);
        }
    };

    public static final AndroidSearchEngine TORLOCK = new AndroidSearchEngine("TorLock", Constants.PREF_KEY_SEARCH_USE_TORLOCK, "www.torlock.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorLockSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine TORRENTDOWNLOADS = new AndroidSearchEngine("TorrentDownloads", Constants.PREF_KEY_SEARCH_USE_TORRENTDOWNLOADS, "www.torrentdownloads.me") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentDownloadsSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine LIMETORRENTS = new AndroidSearchEngine("LimeTorrents", Constants.PREF_KEY_SEARCH_USE_LIMETORRENTS, "www.limetorrents.info") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new LimeTorrentsSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine NYAA = new AndroidSearchEngine("Nyaa", Constants.PREF_KEY_SEARCH_USE_NYAA, "nyaa.si") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new NyaaSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine TPB = new AndroidSearchEngine("TPB", Constants.PREF_KEY_SEARCH_USE_TPB, null) {
        private String domainName = null;

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            if (domainName == null) {
                throw new RuntimeException("check your logic, this search performer has no domain name ready");
            }
            return new TPBSearchPerformer(domainName, token, keywords, DEFAULT_TIMEOUT);
        }

        @Override
        protected void postInitWork() {
            // while this is happening TPB.isReady() should be false, as it's initialized with a null domain name.
            new Thread(() -> {
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                domainName = UrlUtils.getFastestMirrorDomain(httpClient, TPBSearchPerformer.getMirrors(), 6000, 6);
            }).start();
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

    public static final AndroidSearchEngine ONE337X = new AndroidSearchEngine("1337x", Constants.PREF_KEY_SEARCH_USE_ONE337X, "www.1377x.to") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new One337xSearchPerformer(getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine IDOPE = new AndroidSearchEngine("Idope", Constants.PREF_KEY_SEARCH_USE_IDOPE, "idope.se") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new IdopeSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine TORRENTZ2 = new AndroidSearchEngine("Torrentz2", Constants.PREF_KEY_SEARCH_USE_TORRENTZ2, "torrentz2.eu") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new Torrentz2SearchPerformer(token, keywords, DEFAULT_TIMEOUT / 2);
        }
    };

    public static final AndroidSearchEngine MAGNETDL = new AndroidSearchEngine("MagnetDL", Constants.PREF_KEY_SEARCH_USE_MAGNETDL, "magnetdl.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MagnetDLSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine GLOTORRENTS = new AndroidSearchEngine("GloTorrents", Constants.PREF_KEY_SEARCH_USE_GLOTORRENTS, "gtdb.to") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new GloTorrentsSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine TORRENTSCSV = new AndroidSearchEngine("TorrentsCSV", Constants.PREF_KEY_SEARCH_USE_TORRENTSCSV, "torrents-csv.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentsCSVSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine TELLURIDE_COURIER = new AndroidSearchEngine("Telluride Courier", Constants.PREF_KEY_SEARCH_USE_TELLURIDE_COURIER, "*") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return null;
        }

        @Override
        public Object getTelluridePerformer(long token, String pageUrl, Object adapter) {
            if (adapter instanceof SearchResultListAdapter) {
                return new TellurideCourier.SearchPerformer<>(token, pageUrl, (SearchResultListAdapter) adapter);
            }
            return super.getTelluridePerformer(token, pageUrl, adapter);
        }
    };

    private static final AndroidSearchEngine BT_DIGG = new AndroidSearchEngine("BTDigg", Constants.PREF_KEY_SEARCH_USE_BT_DIGG, "btdig.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BTDiggSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final AndroidSearchEngine YT = new AndroidSearchEngine("YT", Constants.PREF_KEY_SEARCH_USE_YT, "www.youtube.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YTSearchPerformer(token, keywords, DEFAULT_TIMEOUT, 1);
        }

        @Override
        public boolean isActive() {
            return !Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG;
        }
    };

    // List of all engines
    private static final List<AndroidSearchEngine> ALL_ENGINES = Arrays.asList(
            YT,
            MAGNETDL,
            TORRENTZ2,
            ONE337X,
            IDOPE,
            FROSTCLICK,
            TPB,
            BT_DIGG,
            SOUNDCLOUD,
            ARCHIVE,
            TORLOCK,
            TORRENTDOWNLOADS,
            LIMETORRENTS,
            NYAA,
            GLOTORRENTS,
            TORRENTSCSV);

    /**
     * Get all available search engines, optionally excluding non-ready ones
     */
    public static List<AndroidSearchEngine> getEngines(boolean excludeNonReady) {
        List<AndroidSearchEngine> result = new java.util.ArrayList<>();
        for (AndroidSearchEngine engine : ALL_ENGINES) {
            if (!excludeNonReady || engine.isReady()) {
                result.add(engine);
            }
        }
        return result;
    }

    /**
     * Find a search engine by name
     */
    public static AndroidSearchEngine forName(String name) {
        for (AndroidSearchEngine engine : ALL_ENGINES) {
            if (engine.getName().equalsIgnoreCase(name)) {
                return engine;
            }
        }
        return null;
    }
}