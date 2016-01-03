/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui;

import android.os.Build;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.logging.Logger;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.archiveorg.ArchiveorgSearchPerformer;
import com.frostwire.search.bitsnoop.BitSnoopSearchPerformer;
import com.frostwire.search.btjunkie.BtjunkieSearchPerformer;
import com.frostwire.search.extratorrent.ExtratorrentSearchPerformer;
import com.frostwire.search.eztv.EztvSearchPerformer;
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.kat.KATSearchPerformer;
import com.frostwire.search.mininova.MininovaSearchPerformer;
import com.frostwire.search.monova.MonovaSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yify.YifySearchPerformer;
import com.frostwire.search.youtube.YouTubeSearchPerformer;

import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class SearchEngine {
    private static final Logger LOG = Logger.getLogger(SearchEngine.class);
    public static final UserAgent FROSTWIRE_ANDROID_USER_AGENT = new UserAgent(getOSVersionString(), Constants.FROSTWIRE_VERSION_STRING, Constants.FROSTWIRE_BUILD);
    private static final int DEFAULT_TIMEOUT = 10000;

    private final String name;
    private final String preferenceKey;

    private boolean active;

    private SearchEngine(String name, String preferenceKey) {
        this.name = name;
        this.preferenceKey = preferenceKey;
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public abstract SearchPerformer getPerformer(long token, String keywords);

    public String getPreferenceKey() {
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

    public static List<SearchEngine> getEngines() {
        return ALL_ENGINES;
    }

    public static SearchEngine forName(String name) {
        for (SearchEngine engine : getEngines()) {
            if (engine.getName().equalsIgnoreCase(name)) {
                return engine;
            }
        }

        return null;
    }

    static String getOSVersionString() {
        return Build.VERSION.CODENAME + "_" + Build.VERSION.INCREMENTAL + "_" + Build.VERSION.RELEASE + "_" + Build.VERSION.SDK_INT;
    }

    public static final SearchEngine EXTRATORRENT = new SearchEngine("Extratorrent", Constants.PREF_KEY_SEARCH_USE_EXTRATORRENT) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ExtratorrentSearchPerformer("extratorrent.cc", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine MININOVA = new SearchEngine("Mininova", Constants.PREF_KEY_SEARCH_USE_MININOVA) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MininovaSearchPerformer("www.mininova.org", token, keywords, DEFAULT_TIMEOUT);
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
            return new FrostClickSearchPerformer("api.frostclick.com", token, keywords, DEFAULT_TIMEOUT, FROSTWIRE_ANDROID_USER_AGENT);
        }
    };

    public static final SearchEngine BITSNOOP = new SearchEngine("BitSnoop", Constants.PREF_KEY_SEARCH_USE_BITSNOOP) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BitSnoopSearchPerformer("bitsnoop.com", token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TORLOCK = new SearchEngine("TorLock", Constants.PREF_KEY_SEARCH_USE_TORLOCK) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorLockSearchPerformer("www.torlock.com", token, keywords, DEFAULT_TIMEOUT);
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
            TPBSearchPerformer performer = null;
            if (NetworkManager.instance().isDataWIFIUp()) {
                performer = new TPBSearchPerformer("thepiratebay.se", token, keywords, DEFAULT_TIMEOUT);
            } else {
                LOG.info("No TPBSearchPerformer, WiFi not up");
            }
            return performer;
        }
    };

    public static final SearchEngine MONOVA = new SearchEngine("Monova", Constants.PREF_KEY_SEARCH_USE_MONOVA) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            MonovaSearchPerformer performer = null;
            if (NetworkManager.instance().isDataWIFIUp()) {
                performer = new MonovaSearchPerformer("www.monova.org", token, keywords, DEFAULT_TIMEOUT);
            } else {
                LOG.info("No MonovaSearchPerformer, WiFi not up");
            }
            return performer;
        }
    };

    public static final SearchEngine YIFY = new SearchEngine("Yify", Constants.PREF_KEY_SEARCH_USE_YIFY) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            YifySearchPerformer performer = null;
            if (NetworkManager.instance().isDataWIFIUp()) {
                performer = new YifySearchPerformer("www.yify-torrent.org", token, keywords, DEFAULT_TIMEOUT);
            } else {
                LOG.info("No YifySearchPerformer, WiFi not up");
            }
            return performer;
        }
    };

    public static final SearchEngine BTJUNKIE = new SearchEngine("Btjunkie.eu", Constants.PREF_KEY_SEARCH_USE_BTJUNKIE) {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            BtjunkieSearchPerformer performer = null;
            if (NetworkManager.instance().isDataWIFIUp()) {
                performer = new BtjunkieSearchPerformer("btjunkie.eu", token, keywords, DEFAULT_TIMEOUT);
            } else {
                LOG.info("No BtjunkieSearchPerformer, WiFi not up");
            }
            return performer;
        }
    };

    public static final SearchEngine KAT = new SearchEngine("KAT", Constants.PREF_KEY_SEARCH_USE_KAT) {

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            KATSearchPerformer performer = null;
            if (NetworkManager.instance().isDataWIFIUp()) {
                performer = new KATSearchPerformer("kat.cr", token, keywords, DEFAULT_TIMEOUT);
            } else {
                LOG.info("No KATSearchPerformer, WiFi not up");
            }
            return performer;
        }
    };

    private static final List<SearchEngine> ALL_ENGINES = Arrays.asList(EXTRATORRENT, KAT, YIFY, YOUTUBE, FROSTCLICK, MONOVA, MININOVA, BTJUNKIE, TPB, SOUNCLOUD, ARCHIVE, TORLOCK, BITSNOOP, EZTV);
}
