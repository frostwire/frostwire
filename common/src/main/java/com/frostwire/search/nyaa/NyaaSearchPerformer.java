/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.search.nyaa;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NyaaSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(NyaaSearchPerformer.class);
    private static Pattern pattern;

    public NyaaSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 0);
        if (pattern == null) {
            pattern = Pattern.compile(
                    "(?is)<tr class=\"default\">.*?" +
                            "<img src=\"(?<thumbnailurl>.*?)\" alt=.*?" +
                            "<a href=\".*?\" class=\"comments\" title=\".*?\">.*?<i class=\"fa fa-comments-o\"></i>.*?" +
                            "<a href=\"(?<detailsurl>.*?)\" title=\"(?<displayname>.*?)\">.*?<td class=\"text-center\">.*?" +
                            "<a href=\"(?<torrenturl>.*?)\"><i class=\"fa fa-fw fa-download\"></i></a>.*?" +
                            "<a href=\"(?<magneturl>.*?)\"><i class=\"fa fa-fw fa-magnet\"></i></a>.*?" +
                            "<td class=\"text-center\">(?<filesize>.*?)</td>.*?" +
                            "<td class=\"text-center\" data-timestamp=\"(?<timestamp>.*?)\">.*?" +
                            "<td class=\"text-center\">(?<seeds>.*?)</td>");
        }
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/?f=0&c=0_0&q=" + encodedKeywords;
    }

    @Override
    protected List<? extends NyaaSearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            return Collections.emptyList();
        }
        int offset = page.indexOf("</thead>");
        if (offset == -1) {
            offset = 0;
        }
        ArrayList<NyaaSearchResult> results = new ArrayList<>(0);
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(page.substring(offset))));
        boolean matcherFound;
        int MAX_RESULTS = 75;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("searchPage() has failed.\n" + t.getMessage(), t);
            }
            if (matcherFound) {
                NyaaSearchResult sr = new NyaaSearchResult("https://" + getDomainName(), matcher);
                if (sr != null) {
                    results.add(sr);
                }
            } else {
                LOG.warn("NyaaSearchPerformer search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
            }
        } while (matcherFound && !isStopped() && results.size() <= MAX_RESULTS);
        LOG.info("searchPage() got " + results.size() + " results");
        return results;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
