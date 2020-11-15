/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.zooqle;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author aldenml
 * @author gubatron
 */
public final class ZooqleSearchPerformer extends TorrentRegexSearchPerformer<ZooqleSearchResult> {
    //private static final Logger LOG = Logger.getLogger(ZooqleSearchPerformer.class);
    private static final int MAX_RESULTS = 30;
    private static final String PRELIMINARY_RESULTS_REGEX =
            "(?is)<i class=\".*?text-muted2 zqf-small pad-r2\"></i><a class=\".*?small\" href=\"/(?<detailPath>.*?).html\">.*?</a>";
    private static final String HTML_DETAIL_REGEX =
            "(?is)" +
                    "<h4 id=\"torname\">(?<filename>.*?)<span class=\"text-muted4 pad-r2\">.torrent</span>.*" +
                    "title=\"Torrent cloud statistics\"></i><div class=\"progress prog trans..\" title=\"Seeders: (?<seeds>\\d+).*" +
                    "<i class=\"zqf zqf-files text-muted3 pad-r2 trans80\"(?<sizedata>.*?)</span><span class=\"spacer\">.*" +
                    "<i class=\"zqf zqf-time text-muted3 pad-r2 trans80\" title=\"Date indexed\"></i>(?<month>.{3}) (?<day>\\d{1,2}), (?<year>\\d{4}) <span class=\"small pad-l\".*" +
                    "<a rel=\"nofollow\" href=\"magnet:\\?xt=urn:btih:(?<magnet>.*)\"><i class=\"spr dl-magnet pad-r2\"></i>Magnet.*?";

    public ZooqleSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, PRELIMINARY_RESULTS_REGEX, HTML_DETAIL_REGEX);
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        return new ZooqleTempSearchResult(getDomainName(), matcher.group("detailPath") + ".html");
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search?pg=" + page + "&q=" + encodedKeywords + "&s=ns&v=t&sd=d";
    }

    @Override
    protected ZooqleSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new ZooqleSearchResult(sr.getDetailsUrl(), "https://" + getDomainName(), matcher);
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String page) {
        return page.indexOf("<i class=\"spr feed\"></i>");
    }

    @Override
    protected int preliminaryHtmlSuffixOffset(String page) {
        int offset = page.indexOf("Time:");
        if (offset == -1) {
            return super.preliminaryHtmlSuffixOffset(page);
        }
        return offset;
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("<h4 id=\"torname\">");
        if (offset == -1) {
            return super.htmlPrefixOffset(html);
        }
        return offset - 20;
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("Language:");
        if (offset == -1) {
            return super.htmlSuffixOffset(html);
        }
        return offset;
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }
//    public static void main(String[] args) throws Throwable {
////        String TEST_QUERY_TERM = "foobar";
////        String URL_PREFIX = "https://zooqle.com/";
////
////        HttpClient client = HttpClientFactory.newInstance();
////        String preliminaryResultsString = client.get(URL_PREFIX + "search?q=" + TEST_QUERY_TERM + "&s=ns&v=t&sd=d");
////        Pattern preliminaryResultsPattern = Pattern.compile(PRELIMINARY_RESULTS_REGEX);
////        SearchMatcher preliminaryMatcher = SearchMatcher.from(preliminaryResultsPattern.matcher(preliminaryResultsString));
////        int found = 0;
////        while (preliminaryMatcher.find()) {
////        while (found == 0){
////            found++;
////            String detailsUrl = URL_PREFIX + preliminaryMatcher.group("detailPath") + ".html";
//            String detailsUrl = "https://zooqle.com/foo.html";
//            System.out.println("Fetching " + detailsUrl + " ...");
//            //String htmlDetailsString = client.get(new String(detailsUrl.getBytes(), Charset.forName("UTF-8")));
//            String htmlDetailsString = FileUtils.readFileToString(new File("/Users/foo/Desktop/zooqle.html"));
//
//            Pattern htmlDetailPattern = Pattern.compile(HTML_DETAIL_REGEX);
//            System.out.println("SEARCH_RESULTS_REGEX: " + htmlDetailPattern.toString());
//            SearchMatcher detailMatcher = SearchMatcher.from(htmlDetailPattern.matcher(htmlDetailsString));
//
//            if (detailMatcher.find()) {
//
//                System.out.println("filename: [" + detailMatcher.group("filename") + "]");
//                System.out.println("seeds: [" + detailMatcher.group("seeds") + "]");
//                if (detailMatcher.group("sizedata") != null) {
//                    System.out.println("sizedata: " + detailMatcher.group("sizedata"));
//                } else {
//                    System.out.println("warning <sizedata> group not found");
//                }
//                System.out.println("month: " + detailMatcher.group("month"));
//                System.out.println("day: " + detailMatcher.group("day"));
//                System.out.println("year: " + detailMatcher.group("year"));
//
//                System.out.println("magnet: [" + detailMatcher.group("magnet") + "]");
//
//                System.out.print("creationtime: [");
//
//                ZooqleSearchResult sr = new ZooqleSearchResult("https://zooqle.com/blabla.html", "http://zooqle.com", detailMatcher);
//                System.out.println(sr.getCreationTime() + "]");
//                System.out.println("size in bytes: [" + sr.getSize() + "]");
//                System.out.println("ZooqleSearchResult -> " + sr);
//                System.out.println("===");
//            } else {
//                System.out.println("========================================================\n");
//                System.out.println(htmlDetailsString);
//                System.out.println("\n========================================================\n");
//                //System.out.println("HTML_DETAIL_REGEX failed on " + detailsUrl);
//                System.out.println("HTML_DETAIL_REGEX failed");
//                System.out.println(HTML_DETAIL_REGEX);
//                System.out.println("\n========================================================\n");
//                System.out.println("Exiting.");
//                return;
//            }
//
//            System.out.println("========================================================\n");
//        //}
//    }
}
