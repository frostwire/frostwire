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

import com.frostwire.search.SearchPerformer;
import com.frostwire.search.archiveorg.ArchiveorgSearchPerformer;
import com.frostwire.search.eztv.EztvSearchPerformer;
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import com.frostwire.search.pixabay.PixabaySearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yify.YifySearchPerformer;
import com.frostwire.search.youtube.YouTubeSearchPerformer;
import com.frostwire.search.zooqle.ZooqleSearchPerformer;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class SearchEngine {

    enum Type {
        ALL,
        CLOUD,
        TORRENT,
        P2P
    }

    private static final int DEFAULT_TIMEOUT = 10000;

    private final String name;
    private final String preferenceKey;
    private boolean active;
    private static UserAgent frostWireUserAgent = null;

    private SearchEngine(String name, String preferenceKey) {
        this.name = name;
        this.preferenceKey = preferenceKey;
        this.active = true;
    }

    static void initFrostWireUserAgent(UserAgent userAgentString) {
        if (frostWireUserAgent == null) {
            frostWireUserAgent = userAgentString;
        } else {
            throw new RuntimeException("Check your logic, SearchEngine.frostWireUserAgent already initialized");
        }
    }

    public String getName() {
        return name;
    }

    public abstract SearchPerformer getPerformer(long token, String keywords);

    private String getPreferenceKey() {
        return preferenceKey;
    }

    public boolean isEnabled() {
        return isActive() && ConfigurationManager.instance().getBoolean(preferenceKey);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return name;
    }

    static List<SearchEngine> getEngines(Type type) {
        // ensure that at least one is enabled
        boolean oneEnabled = false;
        for (SearchEngine se : ALL_ENGINES) {
            if (se.isEnabled()) {
                oneEnabled = true;
            }
        }
        if (!oneEnabled) {
            SearchEngine engineToEnable;
            engineToEnable = YOUTUBE;

            // null check in case the logic above changes
            if (engineToEnable != null) {
                String prefKey = engineToEnable.getPreferenceKey();
                ConfigurationManager.instance().setBoolean(prefKey, true);
            }
        }
        if (type == Type.ALL) {
            return ALL_ENGINES;
        } else if (type == Type.CLOUD) {
            return CLOUD_ENGINES;
        } else if (type == Type.TORRENT) {
            return TORRENT_ENGINES;
        } else if (type == Type.P2P) {
            throw new RuntimeException("Error: P2P search engine not implemented yet");
        }
        return ALL_ENGINES;
    }

    public static SearchEngine forName(String name) {
        for (SearchEngine engine : getEngines(Type.ALL)) {
            if (engine.getName().equalsIgnoreCase(name)) {
                return engine;
            }
        }

        return null;
    }

    public static final SearchEngine ZOOQLE = new SearchEngine("Zooqle", Constants.PREF_KEY_SEARCH_USE_ZOOQLE) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ZooqleSearchPerformer("zooqle.com", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine YOUTUBE = new SearchEngine("YouTube", Constants.PREF_KEY_SEARCH_USE_YOUTUBE) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YouTubeSearchPerformer("www.youtube.com", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine SOUNCLOUD = new SearchEngine("Soundcloud", Constants.PREF_KEY_SEARCH_USE_SOUNDCLOUD) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer("api.sndcdn.com", token, keywords, DEFAULT_TIMEOUT);
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
            return new FrostClickSearchPerformer("api.frostclick.com", token, keywords, DEFAULT_TIMEOUT, SearchEngine.frostWireUserAgent);
        }
    };

    public static final SearchEngine TORLOCK = new SearchEngine("TorLock", Constants.PREF_KEY_SEARCH_USE_TORLOCK) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorLockSearchPerformer("www.torlock.com", token, keywords, DEFAULT_TIMEOUT);
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
            return new LimeTorrentsSearchPerformer("www.limetorrents.cc", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine EZTV = new SearchEngine("Eztv", Constants.PREF_KEY_SEARCH_USE_EZTV) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new EztvSearchPerformer("eztv.ag", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TPB = new SearchEngine("TPB", Constants.PREF_KEY_SEARCH_USE_TPB) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TPBSearchPerformer("thepiratebay.org", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine YIFY = new SearchEngine("Yify", Constants.PREF_KEY_SEARCH_USE_YIFY) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YifySearchPerformer("www.yify-torrent.org", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine PIXABAY = new SearchEngine("Pixabay", Constants.PREF_KEY_SEARCH_USE_PIXABAY) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new PixabaySearchPerformer(token, keywords, DEFAULT_TIMEOUT);
        }
    };

    private static final List<SearchEngine> ALL_ENGINES = Arrays.asList(
            YIFY,
            YOUTUBE,
            FROSTCLICK,
            ZOOQLE,
            TPB,
            SOUNCLOUD,
            ARCHIVE,
            PIXABAY,
            TORLOCK,
            TORRENTDOWNLOADS,
            LIMETORRENTS,
            EZTV);

    private static final List<SearchEngine> CLOUD_ENGINES = Arrays.asList(
            YOUTUBE,
            SOUNCLOUD,
            ARCHIVE,
            PIXABAY
    );

    private static final List<SearchEngine> TORRENT_ENGINES = Arrays.asList(
            YIFY,
            FROSTCLICK,
            ZOOQLE,
            TPB,
            TORLOCK,
            TORRENTDOWNLOADS,
            LIMETORRENTS,
            EZTV);
}
