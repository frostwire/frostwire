/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.eztv;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class EztvSearchPerformer extends TorrentRegexSearchPerformer<EztvSearchResult> {
    public static final String SEARCH_RESULTS_REGEX = "(?is)<a href=\"(/ep/.*?)\"";
    // This is a good example of optional regex groups when a page might have different possible formats to parse.
    public static final String TORRENT_DETAILS_PAGE_REGEX =
            "(?is)<td class=\"section_post_header\" colspan=\"2\"><h1><span style.*?>(?<displaynamefallback>.*?)</span></h1></td>.*?" +
                    "Download Links.*?" +
                    ".*<a href=\"(?<torrenturl>http(s)?.*?\\.torrent)\" (title=\"Download Torrent\"|class=\"download_.\").*?" +
                    ".*<a href=\"(?<magneturl>magnet:\\?.*?)\" (class=\"magnet\"|title=\"Magnet Link\").*?" +
                    "Seeds: <span.*?>(?<seeds>.*?)</span><br.*?" +
                    "(Torrent Info.*?title=\"(?<displayname>.*?)\".*?)?" +
                    "(<b>Torrent File:</b>\\s+(?<displayname2>.*?)<br.*?)?" +
                    "(<b>Torrent Hash:</b>\\s+(?<infohash>.*?)<br.*?)?" +
                    "<b>Filesize:</b>\\s+(?<filesize>.*?)<br.*?" +
                    "<b>Released:</b>\\s+(?<creationtime>.*?)<br";
    private static final int MAX_RESULTS = 20;

    public EztvSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, SEARCH_RESULTS_REGEX, TORRENT_DETAILS_PAGE_REGEX);
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new EztvTempSearchResult(getDomainName(), itemId);
    }

    @Override
    protected String fetchSearchPage(String url) {
        Map<String, String> formData = new HashMap<>();
        formData.put("SearchString1", getEncodedKeywords());
        formData.put("SearchString", "");
        formData.put("search", "Search");
        String page = post(url, formData);
        return page != null && isValidHtml(page) ? page : null;
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords;
    }

    @Override
    protected EztvSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new EztvSearchResult(sr.getDetailsUrl(), matcher);
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("id=\"searchsearch_submit\"");
        return offset > 0 ? offset : 0;
    }

    // EZTV is very simplistic in the search engine
    // just a simple keyword check allows to discard the page
    protected boolean isValidHtml(String html) {
        if (html == null || html.contains("Cloudflare")) {
            return false;
        }
        String[] keywords = getKeywords().split(" ");
        String k = null;
        // select the first keyword with length >= 3
        for (int i = 0; k == null && i < keywords.length; i++) {
            String s = keywords[i];
            if (s.length() >= 3) {
                k = s;
            }
        }
        if (k == null) {
            k = keywords[0];
        }
        int count = StringUtils.countMatches(html.toLowerCase(Locale.US), k.toLowerCase(Locale.US));
        return count > 9;
    }
}
