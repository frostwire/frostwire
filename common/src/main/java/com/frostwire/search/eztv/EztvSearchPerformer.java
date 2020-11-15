/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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
