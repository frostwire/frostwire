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

import com.frostwire.search.SearchPerformer;
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
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.torrentz2.Torrentz2SearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yt.YTSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.OSUtils;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.BooleanSetting;

import java.util.Arrays;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchEngine {
    //private static final Logger LOG = Logger.getLogger(SearchEngine.class);
    private static final int DEFAULT_TIMEOUT = 5000;

    public enum SearchEngineID {
        BT_DIGG,
        TPB_ID,
        SOUNDCLOUD_ID,
        ARCHIVEORG_ID,
        FROSTCLICK_ID,
        TORLOCK_ID,
        EZTV_ID,
        YIFI_ID,
        ONE337X_ID,
        IDOPE_ID,
        TORRENTDOWNLOADS_ID,
        LIMETORRENTS_ID,
        ZOOQLE_ID,
        NYAA_ID,
        TORRENTZ2_ID,
        MAGNETDL_ID,
        TORRENTPARADISE_ID,
        GLOTORRENTS_ID,
        TELLURIDE_ID,
        YT_ID
    }

    private static final SearchEngine BT_DIGG = new SearchEngine(SearchEngineID.BT_DIGG, "BTDigg", SearchEnginesSettings.BT_DIGG_SEARCH_ENABLED, "btdig.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BTDiggSearchPerformer(token, keywords, DEFAULT_TIMEOUT * 6);
        }
    };

    private static final SearchEngine TPB = new SearchEngine(SearchEngineID.TPB_ID, "TPB", SearchEnginesSettings.TPB_SEARCH_ENABLED, null) {
        protected void postInitWork() {
            // while this is happening TPB.isReady() should be false, as it's initialized with a null domain name.
            new Thread(() -> {
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                // from https://piratebayproxy.info/
                TPB._domainName = UrlUtils.getFastestMirrorDomain(httpClient, TPBSearchPerformer.getMirrors(), 1000, 6);
            }
            ).start();
        }

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            if (!isReady()) {
                throw new RuntimeException("Check your logic, a search performer that's not ready should not be in the list of performers yet.");
            }
            return new TPBSearchPerformer(TPB.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine SOUNDCLOUD = new SearchEngine(SearchEngineID.SOUNDCLOUD_ID, "Soundcloud", SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED, "api-v2.soundcloud.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer(SOUNDCLOUD.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine ARCHIVEORG = new SearchEngine(SearchEngineID.ARCHIVEORG_ID, "Archive.org", SearchEnginesSettings.ARCHIVEORG_SEARCH_ENABLED, "archive.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ArchiveorgSearchPerformer(ARCHIVEORG.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine FROSTCLICK = new SearchEngine(SearchEngineID.FROSTCLICK_ID, "FrostClick", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED, "api.frostclick.com") {
        private final UserAgent userAgent = new UserAgent(OSUtils.getFullOS(), FrostWireUtils.getFrostWireVersion(), String.valueOf(FrostWireUtils.getBuildNumber()));

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer(FROSTCLICK.getDomainName(), token, keywords, DEFAULT_TIMEOUT, userAgent);
        }
    };
    private static final SearchEngine TORLOCK = new SearchEngine(SearchEngineID.TORLOCK_ID, "TorLock", SearchEnginesSettings.TORLOCK_SEARCH_ENABLED, "www.torlock.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorLockSearchPerformer(TORLOCK.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine TORRENTDOWNLOADS = new SearchEngine(SearchEngineID.TORRENTDOWNLOADS_ID, "TorrentDownloads", SearchEnginesSettings.TORRENTDOWNLOADS_SEARCH_ENABLED, "www.torrentdownloads.me") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentDownloadsSearchPerformer(TORRENTDOWNLOADS.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine LIMETORRENTS = new SearchEngine(SearchEngineID.LIMETORRENTS_ID, "LimeTorrents", SearchEnginesSettings.LIMETORRENTS_SEARCH_ENABLED, "www.limetorrents.info") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new LimeTorrentsSearchPerformer(LIMETORRENTS.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine NYAA = new SearchEngine(SearchEngineID.NYAA_ID, "Nyaa", SearchEnginesSettings.NYAA_SEARCH_ENABLED, "nyaa.si") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new NyaaSearchPerformer("nyaa.si", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final SearchEngine ONE337X = new SearchEngine(SearchEngineID.ONE337X_ID, "1337x", SearchEnginesSettings.ONE337X_SEARCH_ENABLED, "www.1377x.to") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new One337xSearchPerformer(ONE337X.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine IDOPE = new SearchEngine(SearchEngineID.IDOPE_ID, "Idope", SearchEnginesSettings.IDOPE_SEARCH_ENABLED, "idope.se") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new IdopeSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine TORRENTZ2 = new SearchEngine(SearchEngineID.TORRENTZ2_ID, "Torrentz2", SearchEnginesSettings.TORRENTZ2_SEARCH_ENABLED, "torrentz2.eu") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new Torrentz2SearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine MAGNETDL = new SearchEngine(SearchEngineID.MAGNETDL_ID, "MagnetDL", SearchEnginesSettings.MAGNETDL_ENABLED, "magnetdl.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MagnetDLSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine GLOTORRENTS = new SearchEngine(SearchEngineID.GLOTORRENTS_ID, "GloTorrents", SearchEnginesSettings.GLOTORRENTS_ENABLED, "gtdb.to") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new GloTorrentsSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final SearchEngine TELLURIDE = new SearchEngine(SearchEngineID.TELLURIDE_ID, "Cloud Backup", SearchEnginesSettings.TELLURIDE_ENABLED, "*") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TellurideSearchPerformer(token,
                    keywords,
                    new TellurideSearchPerformerDesktopListener(),
                    FrostWireUtils.getTellurideLauncherFile());
        }
    };

    private static final SearchEngine YT = new SearchEngine(SearchEngineID.YT_ID, "YT", SearchEnginesSettings.YT_SEARCH_ENABLED, "www.youtube.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YTSearchPerformer(token, keywords, DEFAULT_TIMEOUT, 1);
        }
    };

    private final SearchEngineID _id;
    private final String _name;
    private final BooleanSetting _setting;
    private String redirectUrl = null;
    private String _domainName;

    private SearchEngine(SearchEngineID id, String name, BooleanSetting setting, String domainName) {
        _id = id;
        _name = name;
        _setting = setting;
        _domainName = domainName;
        postInitWork();
    }

    /**
     * Override for things like picking the fastest mirror domainName
     */
    protected void postInitWork() {
    }


    // desktop/ is currently using this class, but it should use common/SearchManager.java in the near future (like android/)
    public static List<SearchEngine> getEngines() {
        return Arrays.asList(
                YT,
                BT_DIGG,
                ARCHIVEORG,
                GLOTORRENTS,
                IDOPE,
                LIMETORRENTS,
                MAGNETDL,
                NYAA,
                ONE337X,
                TPB,
                TORLOCK,
                TORRENTDOWNLOADS,
                TORRENTZ2,
                SOUNDCLOUD,
                FROSTCLICK);
    }

    static SearchEngine getSearchEngineByName(String name) {
        if (name.startsWith("Cloud:")) {
            return TELLURIDE;
        }
        return getEngines().stream().
                filter(se -> name.startsWith(se.getName())).findFirst().
                orElse(null);
    }

    public static SearchEngine getSearchEngineByID(SearchEngineID id) {
        if (id == SearchEngineID.TELLURIDE_ID) {
            return TELLURIDE;
        }
        for (SearchEngine engine : getEngines()) {
            if (engine.getId() == id) {
                return engine;
            }
        }
        return null;
    }


    public SearchEngineID getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    private String getDomainName() {
        return _domainName;
    }

    public boolean isEnabled() {
        return _setting.getValue();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SearchEngine && _id == ((SearchEngine) obj)._id;
    }

    @Override
    public int hashCode() {
        return _id.ordinal();
    }

    public abstract SearchPerformer getPerformer(long token, String keywords);

    public BooleanSetting getEnabledSetting() {
        return _setting;
    }

    @SuppressWarnings("unused")
    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public boolean isReady() {
        return _domainName != null;
    }
}
