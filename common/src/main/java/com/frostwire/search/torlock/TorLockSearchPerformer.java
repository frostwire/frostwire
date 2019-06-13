/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torlock;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorLockSearchPerformer extends TorrentRegexSearchPerformer<TorLockSearchResult> {
    private static final int MAX_RESULTS = 15;
    private static final String REGEX = "(?is)<a href=/torrent/([0-9]*?/.*?\\.html)>";
    private static final String HTML_REGEX = "(?is)<a href=\"/tor/(?<torrentid>.*?).torrent\".*?" +
            "<dt>NAME</dt>.?<dd>(?<filename>.*?).torrent</dd>.*?" +
            "<dt>INFOHASH</dt><dd.*?>(?<infohash>.*?)</dd>.*?" +
            "<dt>SIZE</dt>.?<dd>(?<filesize>.*?) in.*?" +
            "<dt>ADDED</dt>.?<dd>Uploaded on (?<time>.*?) by.*?" +
            "<dt>SWARM</dt>.?<dd><b style=\"color:#FF5400\">(?<seeds>\\d*?)</b>";

    public TorLockSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("0%20", "-");
        return "https://" + getDomainName() + "/all/torrents/" + transformedKeywords + ".html";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new TorLockTempSearchResult(getDomainName(), itemId);
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("SIGN UP</a>");
        return offset > 0 ? offset : 0;
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf(">Description</a></li>");
        return offset > 0 ? offset : 0;
    }

    @Override
    protected TorLockSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new TorLockSearchResult(getDomainName(), sr.getDetailsUrl(), matcher);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }

 /*
 public static void main(String[] args) throws Exception {
     String TEST_SEARCH_TERM = "foo bar";
     String URL_PREFIX = "https://www.torlock.com";
     HttpClient httpClient = HttpClientFactory.newInstance();
     String resultsHTML = httpClient.get(URL_PREFIX + "/all/torrents/" + TEST_SEARCH_TERM + ".html", 10000);
     System.out.println("Downloaded " + resultsHTML.length() + " bytes from search result page");
     final Pattern resultsPattern = Pattern.compile(SEARCH_RESULTS_REGEX);
     final SearchMatcher matcher = SearchMatcher.from(resultsPattern.matcher(resultsHTML));
     while (matcher.find()) {
         String detailsUrl = URL_PREFIX + "/torrent/" + matcher.group(1);
         System.out.println("detailsUrl: [" + detailsUrl + "]");
         System.out.println("Fetching " + detailsUrl + " ...");
         String detailPageHTML = httpClient.get(detailsUrl);
         Pattern detailPattern = Pattern.compile(TORRENT_DETAILS_PAGE_REGEX);
         SearchMatcher detailMatcher = SearchMatcher.from(detailPattern.matcher(detailPageHTML));
         if (!detailMatcher.find()) {
             System.out.println("TORRENT_DETAILS_PAGE_REGEX failed with " + detailsUrl);
             return;
         } else {
             System.out.println("TorrentID: " + detailMatcher.group("torrentid"));
             System.out.println("File name: " + detailMatcher.group("filename").replaceAll("<font color=\".*?\">|</font>",""));
             System.out.println("Size: " + detailMatcher.group("filesize"));
             System.out.println("Date: " + detailMatcher.group("time"));
             System.out.println("Seeds: " + detailMatcher.group("seeds"));
             System.out.println("==\n");
         }
         System.out.println("resting 3 seconds...");
         Thread.sleep(3000);
     }
 }*/
}
