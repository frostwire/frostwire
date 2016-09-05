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

package com.frostwire.search.limetorrents;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author alejandroarturom
 */
public class LimeTorrentsSearchPerformer extends TorrentRegexSearchPerformer<LimeTorrentsSearchResult> {

    private static final int MAX_RESULTS = 20;
    private static final String REGEX = "(?is)<a href=\"http://itorrents.org/torrent/(.*?).torrent?(.*?)\" rel=\"nofollow\" class=\"csprite_dl14\"></a><a href=\"(?<itemid>.*?).html?(.*?)\">.*?</a></div>.*?";
    private static final String HTML_REGEX =
                    "(?is)<h1>(?<filename>.*?)</h1>.*?" + // +
                    "<span class=\"greenish\">Seeders : (?<seeds>\\d*?)</span>.*?" +
                    "<tr><td align=\"right\"><b>Hash</b> :</td><td>(?<torrentid>.*?)</td></tr>.*?" +
                    "<tr><td align=\"right\"><b>Added</b> :</td><td>(?<time>.*?)  in.*?" +
                    "<tr><td align=\"right\"><b>Size</b> :</td><td>(?<filesize>.*?) (?<unit>[A-Z]+)</td></tr>.*?"; // +



    public LimeTorrentsSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("0%20", "-");
        return "https://" + getDomainName() + "/search/all/" + transformedKeywords + "/seeds/1/";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group("itemid");
        String transformedId = itemId.replaceFirst("/", "");
        return new LimeTorrentsTempSearchResult(getDomainName(), transformedId);
    }

        @Override
        protected int htmlPrefixOffset(String html) {
            int offset = html.indexOf("FREE</a>");
            return offset > 0 ? offset : 0;
        }

        @Override
        protected int htmlSuffixOffset(String html) {
            int offset = html.indexOf("<div><h3>Latest Searches</h3>");
            return offset > 0 ? offset : 0;
        }

    @Override
    protected LimeTorrentsSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new LimeTorrentsSearchResult(getDomainName(), sr.getDetailsUrl(), matcher);
    }
/*
 public static void main(String[] args) throws Exception {
 //REGEX TEST CODE


        String resultsHTML = FileUtils.readFileToString(new File("/Users/alejandroarturom/Desktop/testa.html"));
       final Pattern resultsPattern = Pattern.compile(REGEX);

         final SearchMatcher matcher = SearchMatcher.from(resultsPattern.matcher(resultsHTML));
          while (matcher.find()) {
          System.out.println(matcher.group(1));
             System.out.println("TorrentID: " + matcher.group("itemid"));
          }

/*
 String resultHTML = FileUtils.readFileToString(new File("/Users/alejandroarturom/Desktop/single.html"));
 final Pattern detailPattern = Pattern.compile(HTML_REGEX);
 final SearchMatcher detailMatcher = SearchMatcher.from(detailPattern.matcher(resultHTML));

 if (detailMatcher.find()) {

  System.out.println("File name: " + detailMatcher.group("filename"));
  System.out.println("TorrentID: " + detailMatcher.group("torrentid"));
  System.out.println("Size: " + detailMatcher.group("filesize"));
  System.out.println("Unit: " + detailMatcher.group("unit"));
  System.out.println("Date: " + detailMatcher.group("time"));
  System.out.println("Seeds: " + detailMatcher.group("seeds"));
 } else {
 System.out.println("No detail matched.");
 }

 }
*/
}

