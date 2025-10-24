/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.SearchPerformer;
import com.frostwire.search.internetarchive.InternetArchiveSearchPerformer;
import com.frostwire.search.btdigg.BTDiggSearchPerformer;
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.glotorrents.GloTorrentsSearchPerformer;
import com.frostwire.search.idope.IdopeSearchPerformer;
import com.frostwire.search.knaben.KnabenSearchPerformer;
import com.frostwire.search.magnetdl.MagnetDLSearchPerformer;
import com.frostwire.search.nyaa.NyaaSearchPerformer;
import com.frostwire.search.one337x.One337xSearchPerformer;
import com.frostwire.gui.updates.SoundCloudConfigFetcher;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPerformer;
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
        INTERNET_ARCHIVE_ID,
        FROSTCLICK_ID,
        ONE337X_ID,
        IDOPE_ID,
        TORRENTDOWNLOADS_ID,
        NYAA_ID,
        TORRENTZ2_ID,
        MAGNETDL_ID,
        GLOTORRENTS_ID,
        TELLURIDE_ID,
        YT_ID,
        TORRENTSCSV_ID,
        KNABEN_ID
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
            SoundcloudSearchPerformer performer = new SoundcloudSearchPerformer(SOUNDCLOUD.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
            // Inject dynamic credentials fetched from remote server
            performer.setCredentials(SoundCloudConfigFetcher.getClientId(), SoundCloudConfigFetcher.getAppVersion());
            return performer;
        }
    };
    private static final SearchEngine INTERNET_ARCHIVE = new SearchEngine(SearchEngineID.INTERNET_ARCHIVE_ID, "Archive.org", SearchEnginesSettings.INTERNET_ARCHIVE_SEARCH_ENABLED, "archive.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new InternetArchiveSearchPerformer(INTERNET_ARCHIVE.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine FROSTCLICK = new SearchEngine(SearchEngineID.FROSTCLICK_ID, "FrostClick", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED, "api.frostclick.com") {
        private final UserAgent userAgent = new UserAgent(OSUtils.getFullOS(), FrostWireUtils.getFrostWireVersion(), String.valueOf(FrostWireUtils.getBuildNumber()));

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer(FROSTCLICK.getDomainName(), token, keywords, DEFAULT_TIMEOUT, userAgent);
        }
    };
    private static final SearchEngine TORRENTDOWNLOADS = new SearchEngine(SearchEngineID.TORRENTDOWNLOADS_ID, "TorrentDownloads", SearchEnginesSettings.TORRENTDOWNLOADS_SEARCH_ENABLED, "www.torrentdownloads.me") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentDownloadsSearchPerformer(TORRENTDOWNLOADS.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
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
    private static final SearchEngine IDOPE = new SearchEngine(SearchEngineID.IDOPE_ID, "Idope", SearchEnginesSettings.IDOPE_SEARCH_ENABLED, "idope.hair") {
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
    private static final SearchEngine MAGNETDL = new SearchEngine(SearchEngineID.MAGNETDL_ID, "MagnetDL", SearchEnginesSettings.MAGNETDL_ENABLED, "magnetdl.homes") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MagnetDLSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };
    private static final SearchEngine GLOTORRENTS = new SearchEngine(SearchEngineID.GLOTORRENTS_ID, "GloTorrents", SearchEnginesSettings.GLOTORRENTS_ENABLED, "gtso.cc") {
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

    private static final SearchEngine TORRENTSCSV = new SearchEngine(SearchEngineID.TORRENTSCSV_ID, "TorrentsCSV", SearchEnginesSettings.TORRENTSCSV_SEARCH_ENABLED, "torrents-csv.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentsCSVSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final SearchEngine KNABEN = new SearchEngine(SearchEngineID.KNABEN_ID, "Knaben", SearchEnginesSettings.KNABEN_SEARCH_ENABLED, "knaben.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new KnabenSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
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
                INTERNET_ARCHIVE,
                GLOTORRENTS,
                IDOPE,
                KNABEN,
                MAGNETDL,
                NYAA,
                ONE337X,
                TPB,
                TORRENTDOWNLOADS,
                TORRENTZ2,
                TORRENTSCSV,
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
