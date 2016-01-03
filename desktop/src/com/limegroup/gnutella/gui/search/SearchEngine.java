/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

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
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.yify.YifySearchPerformer;
import com.frostwire.search.youtube.YouTubeSearchPerformer;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import org.limewire.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class SearchEngine {

    private static final int DEFAULT_TIMEOUT = 5000;

    public String redirectUrl = null;

    private final int _id;
    private final String _name;
    private final String _domainName;
    private final BooleanSetting _setting;

    public static final int MININOVA_ID = 1;
    public static final int KAT_ID = 8;
    public static final int EXTRATORRENT_ID = 4;
    public static final int TPB_ID = 6;
    public static final int MONOVA_ID = 7;
    public static final int YOUTUBE_ID = 9;
    public static final int SOUNDCLOUD_ID = 10;
    public static final int ARCHIVEORG_ID = 11;
    public static final int FROSTCLICK_ID = 12;
    public static final int BITSNOOP_ID = 13;
    public static final int TORLOCK_ID = 14;
    public static final int EZTV_ID = 15;

    public static final int YIFI_ID = 17;
    public static final int BTJUNKIE_ID = 18;
    
    public static final SearchEngine MININOVA = new SearchEngine(MININOVA_ID, "Mininova", SearchEnginesSettings.MININOVA_SEARCH_ENABLED, "www.mininova.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MininovaSearchPerformer(MININOVA.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine KAT = new SearchEngine(KAT_ID, "KAT", SearchEnginesSettings.KAT_SEARCH_ENABLED, "kat.cr") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new KATSearchPerformer(KAT.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine EXTRATORRENT = new SearchEngine(EXTRATORRENT_ID, "Extratorrent", SearchEnginesSettings.EXTRATORRENT_SEARCH_ENABLED, "extratorrent.cc") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ExtratorrentSearchPerformer(EXTRATORRENT.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TPB = new SearchEngine(TPB_ID, "TPB", SearchEnginesSettings.TPB_SEARCH_ENABLED, "thepiratebay.se") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TPBSearchPerformer(TPB.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine MONOVA = new SearchEngine(MONOVA_ID, "Monova", SearchEnginesSettings.MONOVA_SEARCH_ENABLED, "www.monova.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new MonovaSearchPerformer(MONOVA.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine YOUTUBE = new SearchEngine(YOUTUBE_ID, "YouTube", SearchEnginesSettings.YOUTUBE_SEARCH_ENABLED, "www.youtube.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YouTubeSearchPerformer(YOUTUBE.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine SOUNDCLOUD = new SearchEngine(SOUNDCLOUD_ID, "Soundcloud", SearchEnginesSettings.SOUNDCLOUD_SEARCH_ENABLED, "api.sndcdn.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer(SOUNDCLOUD.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine ARCHIVEORG = new SearchEngine(ARCHIVEORG_ID, "Archive.org", SearchEnginesSettings.ARCHIVEORG_SEARCH_ENABLED, "archive.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new ArchiveorgSearchPerformer(ARCHIVEORG.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine FROSTCLICK = new SearchEngine(FROSTCLICK_ID, "FrostClick", SearchEnginesSettings.FROSTCLICK_SEARCH_ENABLED, "api.frostclick.com") {
        private final UserAgent userAgent = new UserAgent(org.limewire.util.OSUtils.getFullOS(), FrostWireUtils.getFrostWireVersion(), String.valueOf(FrostWireUtils.getBuildNumber()));

        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer(FROSTCLICK.getDomainName(), token, keywords, DEFAULT_TIMEOUT, userAgent);
        }
    };

    public static final SearchEngine BITSNOOP = new SearchEngine(BITSNOOP_ID, "BitSnoop", SearchEnginesSettings.BITSNOOP_SEARCH_ENABLED, "bitsnoop.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BitSnoopSearchPerformer(BITSNOOP.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine TORLOCK = new SearchEngine(TORLOCK_ID, "TorLock", SearchEnginesSettings.TORLOCK_SEARCH_ENABLED, "www.torlock.com") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new TorLockSearchPerformer(TORLOCK.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine EZTV = new SearchEngine(EZTV_ID, "Eztv", SearchEnginesSettings.EZTV_SEARCH_ENABLED, "eztv.ag") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new EztvSearchPerformer(EZTV.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine YIFY = new SearchEngine(YIFI_ID, "Yify", SearchEnginesSettings.YIFY_SEARCH_ENABLED, "www.yify-torrent.org") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new YifySearchPerformer(YIFY.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };

    public static final SearchEngine BTJUNKIE = new SearchEngine(BTJUNKIE_ID, "BTJunkie", SearchEnginesSettings.BTJUNKIE_SEARCH_ENABLED, "btjunkie.eu") {
        @Override
        public SearchPerformer getPerformer(long token, String keywords) {
            return new BtjunkieSearchPerformer(BTJUNKIE.getDomainName(), token, keywords, DEFAULT_TIMEOUT);
        }
    };
    
    public SearchEngine(int id, String name, BooleanSetting setting, String domainName) {
        _id = id;
        _name = name;
        _setting = setting;
        _domainName = domainName;
    }

    public int getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }
    
    public String getDomainName() {
        return _domainName;
    }

    public boolean isEnabled() {
        return _setting.getValue();
    }

    @Override
    public boolean equals(Object obj) {
        return obj !=null && _id == ((SearchEngine) obj)._id;
    }

    @Override
    public int hashCode() {
        return _id;
    }

    public static List<SearchEngine> getEngines() {
        return Arrays.asList(YOUTUBE, EXTRATORRENT, TPB, SOUNDCLOUD, FROSTCLICK, MININOVA, KAT, MONOVA, ARCHIVEORG, TORLOCK, YIFY, BTJUNKIE, BITSNOOP, EZTV);
    }

    public static List<SearchEngine> getActiveEngines() {
        final List<SearchEngine> result = new ArrayList<SearchEngine>();
        final List<SearchEngine> activeEngines = getEngines();

        for (SearchEngine searchEngine : activeEngines) {
            if (searchEngine.isEnabled()) {
                result.add(searchEngine);
            }
        }

        return result;
    }

    public abstract SearchPerformer getPerformer(long token, String keywords);

    public static SearchEngine getSearchEngineByName(String name) {
        List<SearchEngine> searchEngines = getEngines();

        for (SearchEngine engine : searchEngines) {
            if (name.startsWith(engine.getName())) {
                return engine;
            }
        }

        return null;
    }
    
    /**
     * Used in Domain Alias Manifest QA test, don't delete.
     */
    public static SearchEngine getSearchEngineByDefaultDomainName(String domainName) {
        List<SearchEngine> searchEngines = getEngines();

        for (SearchEngine engine : searchEngines) {
            if (domainName.equalsIgnoreCase(engine.getDomainName())) {
                return engine;
            }
        }
        return null;
    }

    public BooleanSetting getEnabledSetting() {
        return _setting;
    }
}
