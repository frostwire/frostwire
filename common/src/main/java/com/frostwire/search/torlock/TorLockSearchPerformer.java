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

package com.frostwire.search.torlock;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorLockSearchPerformer extends TorrentRegexSearchPerformer<TorLockSearchResult> {

    private static final int MAX_RESULTS = 10;
    private static final String REGEX = "(?is)<a href=/torrent/([0-9]*?/.*?\\.html)>";
    private static final String HTML_REGEX = "(?is).*?<a href=\"/tor/(?<torrentid>.*?).torrent\">.*?" +
            "<dt>NAME</dt><dd>(?<filename>.*?).torrent</dd>.*?" +
            "<dt>SIZE</dt><dd>(?<filesize>.*?) in.*?" +
            "<dt>ADDED</dt><dd>Uploaded on (?<time>.*?) by.*?" +
            "<dt>SWARM</dt><dd><b style=\"color:#FF5400\">(?<seeds>\\d*?)</b>";

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


     /**
     public static void main(String[] args) throws Exception {
         //REGEX TEST CODE

//         String resultsHTML = FileUtils.readFileToString(new File("/Users/gubatron/Desktop/torlock-results.html"));
//         final Pattern resultsPattern = Pattern.compile(REGEX);
//
//         final SearchMatcher matcher = SearchMatcher.from(resultsPattern.matcher(resultsHTML));
//         while (matcher.find()) {
//         System.out.println(matcher.group(1));
//         }


         String resultHTML = FileUtils.readFileToString(new File("/Users/gubatron/Desktop/torlock-result.html"));
         final Pattern detailPattern = Pattern.compile(HTML_REGEX);
         final SearchMatcher detailMatcher = SearchMatcher.from(detailPattern.matcher(resultHTML));

         if (detailMatcher.find()) {
             System.out.println("TorrentID: " + detailMatcher.group("torrentid"));
             System.out.println("File name: " + detailMatcher.group("filename"));
             System.out.println("Size: " + detailMatcher.group("filesize"));
             System.out.println("Date: " + detailMatcher.group("time"));
             System.out.println("Seeds: " + detailMatcher.group("seeds"));
         } else {
             System.out.println("No detail matched.");
         }
     }
     */
}
