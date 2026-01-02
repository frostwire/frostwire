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

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.frostclick.FrostClickSearchPattern;
import com.frostwire.search.nyaa.NyaaSearchPattern;
import com.frostwire.search.knaben.KnabenSearchPattern;
import com.frostwire.search.magnetdl.MagnetDLSearchPattern;
import com.frostwire.search.internetarchive.InternetArchiveSearchPattern;
import com.frostwire.search.internetarchive.InternetArchiveCrawlingStrategy;
import com.frostwire.search.tpb.TPBSearchPattern;
import com.frostwire.search.tpb.TPBMirrors;
import com.frostwire.gui.updates.SoundCloudConfigFetcher;
import com.frostwire.search.soundcloud.SoundcloudSearchPattern;
import com.frostwire.search.telluride.TellurideSearchPerformer;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPattern;
import com.frostwire.search.yt.YTSearchPattern;
import com.frostwire.search.one337x.One337xSearchPattern;
import com.frostwire.search.one337x.One337xCrawlingStrategy;
import com.frostwire.search.glotorrents.GloTorrentsSearchPattern;
import com.frostwire.search.idope.IdopeSearchPattern;
import com.frostwire.search.torrentz2.Torrentz2SearchPattern;
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
        TPB_ID,
        SOUNDCLOUD_ID,
        INTERNET_ARCHIVE_ID,
        FROSTCLICK_ID,
        ONE337X_ID,
        IDOPE_ID,
        NYAA_ID,
        TORRENTZ2_ID,
        MAGNETDL_ID,
        GLOTORRENTS_ID,
        TELLURIDE_ID,
        YT_ID,
        TORRENTSCSV_ID,
        KNABEN_ID
    }

    private static final SearchEngine TPB = new SearchEngine(SearchEngineID.TPB_ID, "TPB", SearchEnginesSettings.TPB_SEARCH_ENABLED, null) {
        protected void postInitWork() {
            // while this is happening TPB.isReady() should be false, as it's initialized with a null domain name.
            new Thread(() -> {
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                // from https://piratebayproxy.info/
                TPB._domainName = UrlUtils.getFastestMirrorDomain(httpClient, TPBMirrors.getMirrors(), 1000, 6);
            }
            ).start();
        }

        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            if (!isReady()) {
                throw new RuntimeException("Check your logic, a search performer that's not ready should not be in the list of performers yet.");
            }
            // V2: Using new flat architecture with regex pattern for dual HTML format support
            // TPB requires access to domain for URL construction, so create V2 SearchEngine directly
            String encodedKeywords = UrlUtils.encode(keywords);
            com.frostwire.search.SearchPerformer searchEngine = new com.frostwire.search.SearchPerformer(
                    token,
                    keywords,
                    encodedKeywords,
                    new TPBSearchPattern(TPB.getDomainName()),  // Pass domain to pattern
                    null,  // No crawling needed - complete data on search page
                    DEFAULT_TIMEOUT
            );
            return searchEngine;
        }
    };
    private static final SearchEngine SOUNDCLOUD = new SearchEngine(SearchEngineID.SOUNDCLOUD_ID, "Soundcloud", SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED, "api-v2.soundcloud.com") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture with JSON API parsing for audio streaming
            // Get dynamic credentials fetched from remote server
            String clientId = SoundCloudConfigFetcher.getClientId();
            String appVersion = SoundCloudConfigFetcher.getAppVersion();
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new SoundcloudSearchPattern(clientId, appVersion),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine INTERNET_ARCHIVE = new SearchEngine(SearchEngineID.INTERNET_ARCHIVE_ID, "Archive.org", SearchEnginesSettings.INTERNET_ARCHIVE_SEARCH_ENABLED, "archive.org") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture with crawling strategy
            // Search page returns preliminary results, strategy fetches file list from detail pages
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new InternetArchiveSearchPattern(),
                    new InternetArchiveCrawlingStrategy(),
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine FROSTCLICK = new SearchEngine(SearchEngineID.FROSTCLICK_ID, "FrostClick", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED, "api.frostclick.com") {
        private final UserAgent userAgent = new UserAgent(OSUtils.getFullOS(), FrostWireUtils.getFrostWireVersion(), String.valueOf(FrostWireUtils.getBuildNumber()));

        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture with custom headers for API authentication
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new FrostClickSearchPattern(userAgent.toString(), userAgent.getUUID(), userAgent.getHeadersMap()),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine NYAA = new SearchEngine(SearchEngineID.NYAA_ID, "Nyaa", SearchEnginesSettings.NYAA_SEARCH_ENABLED, "nyaa.si") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using SearchPerformerFactory with regex-based HTML parsing pattern
            // NyaaSearchPattern extracts anime torrent metadata from search result table
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new NyaaSearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };

    private static final SearchEngine ONE337X = new SearchEngine(SearchEngineID.ONE337X_ID, "1337x", SearchEnginesSettings.ONE337X_SEARCH_ENABLED, "www.1377x.to") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture with crawling strategy
            // Search page returns crawlable results, strategy fetches detail pages to extract magnet links and metadata
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new One337xSearchPattern(),
                    new One337xCrawlingStrategy(),
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine IDOPE = new SearchEngine(SearchEngineID.IDOPE_ID, "idope", SearchEnginesSettings.IDOPE_SEARCH_ENABLED, "idope.hair") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture with JSON API parsing (no crawling needed)
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new IdopeSearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine TORRENTZ2 = new SearchEngine(SearchEngineID.TORRENTZ2_ID, "torrentz2", SearchEnginesSettings.TORRENTZ2_SEARCH_ENABLED, "torrentz2.nz") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture (no crawling needed - Torrentz2 provides complete data)
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new Torrentz2SearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine MAGNETDL = new SearchEngine(SearchEngineID.MAGNETDL_ID, "magnetdl", SearchEnginesSettings.MAGNETDL_ENABLED, "magnetdl.homes") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture (no crawling needed - MagnetDL provides complete data via JSON API)
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new MagnetDLSearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };
    private static final SearchEngine GLOTORRENTS = new SearchEngine(SearchEngineID.GLOTORRENTS_ID, "glotorrents", SearchEnginesSettings.GLOTORRENTS_ENABLED, "gtso.cc") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture (no crawling needed - GloTorrents provides complete data)
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new GloTorrentsSearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };

    private static final SearchEngine TELLURIDE = new SearchEngine(SearchEngineID.TELLURIDE_ID, "Cloud Backup", SearchEnginesSettings.TELLURIDE_ENABLED, "*") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            return new TellurideSearchPerformer(token,
                    keywords,
                    new TellurideSearchPerformerDesktopListener(),
                    FrostWireUtils.getTellurideLauncherFile());
        }
    };

    private static final SearchEngine YT = new SearchEngine(SearchEngineID.YT_ID, "YT", SearchEnginesSettings.YT_SEARCH_ENABLED, "www.youtube.com") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using new flat architecture
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new YTSearchPattern(),
                    null,  // No crawling needed for YouTube
                    DEFAULT_TIMEOUT
            );
        }
    };

    private static final SearchEngine TORRENTSCSV = new SearchEngine(SearchEngineID.TORRENTSCSV_ID, "TorrentsCSV", SearchEnginesSettings.TORRENTSCSV_SEARCH_ENABLED, "torrents-csv.com") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using SearchPerformerFactory with JSON API pattern
            // TorrentsCSVSearchPattern provides complete torrent metadata via JSON API
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new TorrentsCSVSearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
        }
    };

    private static final SearchEngine KNABEN = new SearchEngine(SearchEngineID.KNABEN_ID, "Knaben", SearchEnginesSettings.KNABEN_SEARCH_ENABLED, "knaben.org") {
        @Override
        public ISearchPerformer getPerformer(long token, String keywords) {
            // V2: Using SearchPerformerFactory with custom pattern that declares POST_JSON HTTP method
            // KnabenSearchPattern specifies HTTP method and constructs the JSON request body
            return SearchPerformerFactory.createSearchPerformer(
                    token,
                    keywords,
                    new KnabenSearchPattern(),
                    null,  // No crawling needed
                    DEFAULT_TIMEOUT
            );
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
                INTERNET_ARCHIVE,
                GLOTORRENTS,
                IDOPE,
                KNABEN,
                MAGNETDL,
                NYAA,
                ONE337X,
                TPB,
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

    public abstract ISearchPerformer getPerformer(long token, String keywords);

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
