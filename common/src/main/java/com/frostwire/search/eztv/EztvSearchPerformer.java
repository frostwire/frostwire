/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

    private static final int MAX_RESULTS = 20;
    private static final String REGEX = "(?is)<a href=\"(/ep/.*?)\"";

    // This is a good example of optional regex groups when a page might have different possible formats to parse.
    private static final String HTML_REGEX =
            "(?is)<td class=\"section_post_header\" colspan=\"2\"><h1><span style.*?>(?<displaynamefallback>.*?)</span></h1></td>.*?" +
                    "Download Links.*?" +
                    ".*<a href=\"(?<magneturl>magnet:\\?.*?)\" class=\"magnet\".*?" +
                    //"(<a href=\"(?<magneturl>magnet:\\?.*?)\" title=\"Magnet Link\".*?)?"+
                    ".*<a href=\"(?<torrenturl>http(s)?.*?\\.torrent)\" class=\"download_.\".*?" +
                    "(Torrent Info.*?title=\"(?<displayname>.*?)\".*?)?" +
                    "(<b>Torrent File:</b>\\s+(?<displayname2>.*?)<br.*?)?" +
                    "(<b>Torrent Hash:</b>\\s+(?<infohash>.*?)<br.*?)?" +
                    "<b>Filesize:</b>\\s+(?<filesize>.*?)<br.*?" +
                    "<b>Released:</b>\\s+(?<creationtime>.*?)<br";

    public EztvSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
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
        if (html == null || html.indexOf("Cloudfare") != -1) {
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

    /**
    public static void main(String[] args) throws Throwable {
        String TEST_SEARCH_TERM = "foobar";
        HttpClient httpClient = HttpClientFactory.newInstance();
        String fileStr = httpClient.get("https://eztv.ag/search/" + TEST_SEARCH_TERM);

        Pattern searchResultsDetailURLPattern = Pattern.compile(REGEX);
        Pattern detailPagePattern = Pattern.compile(HTML_REGEX);

        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);

        int found = 0;
        while (searchResultsMatcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("result_url: [" + searchResultsMatcher.group(1) + "]");

            String detailUrl = "https://eztv.ag" + searchResultsMatcher.group(1);
            System.out.println("Fetching details from " + detailUrl + " ....");
            long start = System.currentTimeMillis();
            String detailPage = httpClient.get(detailUrl, 5000);
            if (detailPage == null) {
                System.out.println("Error fetching from " + detailUrl);
                continue;
            }
            long downloadTime = System.currentTimeMillis() - start;
            System.out.println("Downloaded " + detailPage.length() + " bytes in " + downloadTime + "ms");
            SearchMatcher sm = new SearchMatcher(detailPagePattern.matcher(detailPage));

            if (sm.find()) {
                System.out.println("magneturl: [" + sm.group("magneturl") + "]");
                System.out.println("torrenturl: [" + sm.group("torrenturl") + "]");
                System.out.println("displayname: [" + sm.group("displayname") + "]");
                System.out.println("displayname2: [" + sm.group("displayname2") + "]");
                System.out.println("displaynamefallback: [" + sm.group("displaynamefallback") + "]");
                System.out.println("infohash: [" + sm.group("infohash") + "]");
                System.out.println("filesize: [" + sm.group("filesize") + "]");
                System.out.println("creationtime: [" + sm.group("creationtime") + "]");
                EztvSearchResult sr = new EztvSearchResult(detailUrl, sm);
                System.out.println(sr);
            } else {
                System.out.println("Detail page search matcher failed, check HTML_REGEX");
            }
            System.out.println("===");
            System.out.println("Sleeping 5 seconds...");
            Thread.sleep(5000);
        }
        System.out.println("-done-");
    } */
}
