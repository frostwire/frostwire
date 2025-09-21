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

package com.frostwire.android.gui;

import android.os.Build;

import androidx.annotation.NonNull;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.search.SearchPerformer;
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
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.torrentz2.Torrentz2SearchPerformer;
import com.frostwire.search.torrentscsv.TorrentsCSVSearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yt.YTSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.http.HttpClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchEngine {

    private static final UserAgent FROSTWIRE_ANDROID_USER_AGENT = new UserAgent(getOSVersionString(), Constants.FROSTWIRE_VERSION_STRING, Constants.FROSTWIRE_BUILD);
    private static final int DEFAULT_TIMEOUT = 5000;

    private final String name;
    private final String preferenceKey;

    private boolean active;

    private SearchEngine(String name, String preferenceKey) {
        this.name = name;
        this.preferenceKey = preferenceKey;
        this.active = true;
        postInitWork();
    }

    protected boolean isReady() {
        return true;
    }

    protected void postInitWork() {
    }

    public String getName() {
        return name;
    }

    public abstract SearchPerformer getPerformer(long token, String keywords);

    public TellurideCourier.SearchPerformer getTelluridePerformer(long currentSearchToken, String pageUrl, SearchResultListAdapter adapter) {
        // override me
        return null;
    }

    public String getPreferenceKey() {
        return preferenceKey;
    }

    /**
     * This is what's eventually checked to perform a search
     */
    public boolean isEnabled() {
        return isActive() && ConfigurationManager.instance().getBoolean(preferenceKey);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    /**
     * This will include all engines, even if they have been marked as inactive via
     * remote config or by some other mean.
     * <p>
     * It will only exclude non-ready engines if excludeNonReady=true
     * <p>
     * We need to include inactive engines so that places like the SearchEnginesPreferenceFragment
     * can separate active engines from inactive engines and do things like hide them from the
     * user interface in the case of FrostWire Basic where some engines are not allowed in google play
     */
    public static List<SearchEngine> getEngines(boolean excludeNonReady) {
        ArrayList<SearchEngine> candidates = new ArrayList<>();

        for (SearchEngine se : ALL_ENGINES) {
            if (!excludeNonReady || se.isReady()) {
                candidates.add(se);
            }
        }

        // ensure that at least one is enable
        boolean oneEnabled = false;
        for (SearchEngine se : candidates) {
            if (se.isEnabled()) {
                oneEnabled = true;
            }
        }
        if (!oneEnabled) {
            SearchEngine engineToEnable;
            engineToEnable = ARCHIVE;
            String prefKey = engineToEnable.getPreferenceKey();
            ConfigurationManager.instance().setBoolean(prefKey, true);
        }
        return candidates;
    }

    public static SearchEngine forName(String name) {
        for (SearchEngine engine : getEngines(false)) {
            if (engine.getName().equalsIgnoreCase(name)) {
                return engine;
            }
        }
        return null;
    }

    static String getOSVersionString() {
        return Build.VERSION.CODENAME + "_" + Build.VERSION.INCREMENTAL + "_" + Build.VERSION.RELEASE + "_" + Build.VERSION.SDK_INT;
    }

    public static final SearchEngine SOUNCLOUD = new SearchEngine("Soundcloud", Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer("api-v2.sndcdn.com", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine ARCHIVE = new SearchEngine("Archive.org", Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ArchiveorgSearchPerformer("archive.org", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine FROSTCLICK = new SearchEngine("FrostClick", Constants.PREF_KEY_SEARCH_USE_FROSTCLICK) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer("api.frostclick.com", token, keywords, DEFAULT_TIMEOUT, FROSTWIRE_ANDROID_USER_AGENT);
        }
    };

    public static final SearchEngine TORRENTDOWNLOADS = new SearchEngine("TorrentDownloads", Constants.PREF_KEY_SEARCH_USE_TORRENTDOWNLOADS) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentDownloadsSearchPerformer("www.torrentdownloads.me", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine LIMETORRENTS = new SearchEngine("LimeTorrents", Constants.PREF_KEY_SEARCH_USE_LIMETORRENTS) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new LimeTorrentsSearchPerformer("www.limetorrents.info", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine NYAA = new SearchEngine("Nyaa", Constants.PREF_KEY_SEARCH_USE_NYAA) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new NyaaSearchPerformer("nyaa.si", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TPB = new SearchEngine("TPB", Constants.PREF_KEY_SEARCH_USE_TPB) {
        private String domainName = null;

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            if (domainName == null) {
                throw new RuntimeException("check your logic, this search performer has no domain name ready");
            }
            return new TPBSearchPerformer(domainName, token, keywords, DEFAULT_TIMEOUT);
        }

        protected void postInitWork() {
            // while this is happening TPB.isReady() should be false, as it's initialized with a null domain name.
            new Thread(() -> {
                HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                domainName = UrlUtils.getFastestMirrorDomain(httpClient, TPBSearchPerformer.getMirrors(), 6000, 6);
            }
            ).start();
        }

        @Override
        protected boolean isReady() {
            return domainName != null;
        }
    };

    public static final SearchEngine ONE337X = new SearchEngine("1337x", Constants.PREF_KEY_SEARCH_USE_ONE337X) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new One337xSearchPerformer("www.1377x.to", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine IDOPE = new SearchEngine("Idope", Constants.PREF_KEY_SEARCH_USE_IDOPE) {

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new IdopeSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TORRENTZ2 = new SearchEngine("Torrentz2", Constants.PREF_KEY_SEARCH_USE_TORRENTZ2) {

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new Torrentz2SearchPerformer(token, keywords, DEFAULT_TIMEOUT / 2);
        }
    };

    public static final SearchEngine MAGNETDL = new SearchEngine("MagnetDL", Constants.PREF_KEY_SEARCH_USE_MAGNETDL) {

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MagnetDLSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };


    public static final SearchEngine GLOTORRENTS = new SearchEngine("GloTorrents", Constants.PREF_KEY_SEARCH_USE_GLOTORRENTS) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new GloTorrentsSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TORRENTSCSV = new SearchEngine("TorrentsCSV", Constants.PREF_KEY_SEARCH_USE_TORRENTSCSV) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorrentsCSVSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine KNABEN = new SearchEngine("Knaben", Constants.PREF_KEY_SEARCH_USE_KNABEN) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new KnabenSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TELLURIDE_COURIER = new SearchEngine("Telluride Courier", Constants.PREF_KEY_SEARCH_USE_TELLURIDE_COURIER) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return null;
        }

        @Override
        public TellurideCourier.SearchPerformer<SearchResultListAdapter> getTelluridePerformer(long token, String pageUrl, SearchResultListAdapter adapter) {
            return new TellurideCourier.SearchPerformer<>(token, pageUrl, adapter);
        }
    };

    private static final SearchEngine BT_DIGG = new SearchEngine("BTDigg", Constants.PREF_KEY_SEARCH_USE_BT_DIGG) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BTDiggSearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine YT = new SearchEngine("YT", Constants.PREF_KEY_SEARCH_USE_YT) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YTSearchPerformer(token, keywords, DEFAULT_TIMEOUT, 1);
        }

        @Override
        public boolean isActive() {
            return !Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG;
        }
    };

    private static final List<SearchEngine> ALL_ENGINES = Arrays.asList(
            YT,
            MAGNETDL,
            TORRENTZ2,
            ONE337X,
            IDOPE,
            FROSTCLICK,
            TPB,
            BT_DIGG,
            SOUNCLOUD,
            ARCHIVE,
            TORRENTDOWNLOADS,
            LIMETORRENTS,
            NYAA,
            GLOTORRENTS,
            TORRENTSCSV,
            KNABEN);
}
