/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrentparadise;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.PagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentJsonSearchPerformer;
import com.google.gson.Gson;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TorrentParadiseSearchPerformer extends PagedWebSearchPerformer {
    //private static final Logger LOG = Logger.getLogger(TorrentParadiseSearchPerformer.class);
    private final Gson gson;

    public TorrentParadiseSearchPerformer(long token, String keywords, int timeout) {
        super("torrent-paradise.ml", token, keywords, timeout, 1);
        gson = new Gson();
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/api/search?q=" + encodedKeywords;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        // yup, they return a "null" string.
        if ("null".equals(page)) {
            stop();
            return Collections.EMPTY_LIST;
        }

        TPSearchResult[] tpResults = gson.fromJson(page, TPSearchResult[].class);

        List<TorrentParadiseSearchResult> results = new LinkedList<>();
        for (TPSearchResult r : tpResults) {
            TPSearchResult r_torrent = new TPSearchResult(r);
            r_torrent.text += ".torrent";

            results.add(new TorrentParadiseSearchResult(r));
            results.add(new TorrentParadiseSearchResult(r_torrent));
        }
        return results;
    }

    public static class TPSearchResult implements Cloneable {
        String id; // infohash hex
        String text; // title
        long len; // length (bytes)
        int s; // seeds
        int l; // leeches

        // copy constructor
        public TPSearchResult(TPSearchResult orig) {
            id = orig.id;
            text = orig.text;
            len = orig.len;
            s = orig.s;
            l = orig.l;
        }
    }

    @Override
    public void crawl(CrawlableSearchResult sr) {
        // avoid super() warning for not implementing crawl()
        // SIGN: Bad design in class hierarchy.
        //
        // SearchResults which are not crawleable should not implement CrawleableSearchResult
        // many do because they extend from AbstractTorrentSearchResult which for some reason implements that
        // interface wrongly. Composition > Inheritance
        // TODO: Break up SearchResult implementations to not extend from AbstractTorrentSearchResult blindly
        //       and only implement CrawleableSearchResult for those that are actually crawleable.
    }
}
