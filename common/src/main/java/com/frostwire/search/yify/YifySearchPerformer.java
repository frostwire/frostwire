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

package com.frostwire.search.yify;

import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;

/**
 * Search Performer for torrents.com / torrents.fm
 * @author gubatron
 * @author aldenml
 *
 */
public class YifySearchPerformer extends TorrentRegexSearchPerformer<YifySearchResult> {

    private static final int MAX_RESULTS = 21;
    private static final String HTML_REGEX = "(?is)<div class=\"minfo\">.*?<div class=\"cover\"><img src='(.*?)' /></div>.*?<div class=\"name\"><h1>(.*?)</h1>.*?<li><b>Size:</b> (.*?)</li>.*?<li><b>Language:</b> (.*?)</li>.*?li><b>Peers/Seeds:</b> (\\d*?) / (\\d*?)</li>.*?<div class=\"attr\"><a class=\"large button orange\" href=\"(.*?)\">Download Ma";
    // matcher groups: 1 -> cover (url contains date)
    //                 2 -> display name
    //                 3 -> size
    //                 4 -> language
    //                 5 -> peers
    //                 6 -> seeds
    //                 7 -> magnet    

    private static final String REGEX = "(?is)<div class=\"mv\">.*?<h3><a href=['\"]/movie/([0-9]*)/(.*?)['\"] target=\"_blank\" title=\"(.*?)\">";


    public YifySearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords + "/";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        String htmlFileName = matcher.group(2);
        String displayName = matcher.group(3);
        
        return new YifyTempSearchResult(getDomainName(), itemId, htmlFileName, displayName);
    }

    @Override
    protected YifySearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
         return new YifySearchResult(getDomainName(), sr.getDetailsUrl(), matcher);
    }

    /**
    public static void main(String[] args) throws Throwable {
        String TEST_SEARCH_TERM = "foobar";
        String URL_PREFIX = "https://www.yify-torrent.org";
        String DETAIL_URL_PREFIX = URL_PREFIX + "/movie/";
        HttpClient httpClient = HttpClientFactory.newInstance();
        String resultsHTML = httpClient.get(URL_PREFIX + "/search/" + TEST_SEARCH_TERM + "/", 10000);

        Pattern pattern = Pattern.compile(REGEX);
        SearchMatcher sm = SearchMatcher.from(pattern.matcher(resultsHTML));
        resultsHTML = null;
        System.gc();
        int found = 0;
        while (sm.find()) {
            System.out.println("group 1: " + sm.group(1));
            System.out.println("group 2: " + sm.group(2));
            System.out.println("group 3: " + sm.group(3));
            String detailsUrl = DETAIL_URL_PREFIX + sm.group(1) + "/" + sm.group(2);
            String detailHTML = httpClient.get(detailsUrl);
            Pattern detailPattern = Pattern.compile(HTML_REGEX);
            SearchMatcher detailMatcher = SearchMatcher.from(detailPattern.matcher(detailHTML));

            if (!detailMatcher.find()) {
                System.out.println("Check HTML_REGEX, matcher failed.");
                return;
            }
            System.out.println(new YifySearchResult("www.yifi-torrent.org",detailsUrl,detailMatcher));
            System.out.println("====================================\n");
            Thread.sleep(3000);
        }

        if (found == 0) {
            System.out.println("No results in search page, check REGEX");
        }
    }*/
}