/*
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.nyaa;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NyaaSearchPerformer extends TorrentSearchPerformer {
    private static Logger LOG = Logger.getLogger(NyaaSearchPerformer.class);
    private final Pattern pattern;

    public NyaaSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 1);
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

    @Override
    protected String getUrl(int page, String encodedKeywords) {
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
}
