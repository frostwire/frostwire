/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.zooqle;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;
import com.frostwire.util.Logger;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author aldenml
 * @author gubatron
 * Created on 4/17/17.
 */
public class ZooqleSearchPerformer extends TorrentRegexSearchPerformer<ZooqleSearchResult> {
    private static Logger LOG = Logger.getLogger(ZooqleSearchPerformer.class);
    private static final int MAX_RESULTS = 30;
    private static final String PRELIMINARY_RESULTS_REGEX =
            "(?is)<i class=\"zqf zqf-tv text-muted2 zqf-small pad-r2\"></i><a class=\".*?small\"href=\"/(?<detailPath>.*?).html\">.*?</a>";
    private static final String HTML_DETAIL_REGEX = "(?is)<h4 id=torname>(?<filename>.*?)<span.*?" +
            "Seeders: (?<seeds>\\d*).*?" +
            "title=\"File size\"></i>(?<size>[\\d\\.]*) (?<sizeUnit>.*?)<span class.*?" +
            "title=\"Date indexed\"></i>(?<month>.*?) (?<day>[\\d]*), (?<year>[\\d]*) <span.*?" +
            "urn:btih:(?<infohash>.*?)&.*?" +
            "href=\"/download/(?<torrent>.*?)\\.torrent\"";

    public ZooqleSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2, MAX_RESULTS, PRELIMINARY_RESULTS_REGEX, HTML_DETAIL_REGEX);
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        return new ZooqleTempSearchResult(getDomainName(),matcher.group("detailPath")+".html");
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search?pg="+page+"&q="+encodedKeywords+"&s=ns&v=t&sd=d";
    }

    @Override
    protected ZooqleSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new ZooqleSearchResult(sr.getDetailsUrl(), "https://" + getDomainName(), matcher);
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String page) {
        int offset = page.indexOf("<i class=\"spr feed\"></i>");
        if (offset == -1) {
            LOG.warn("preliminaryHtmlPrefixOffset() failed to find marker.");
            return super.preliminaryHtmlPrefixOffset(page);
        }
        return offset;
    }

    @Override
    protected int preliminaryHtmlSuffixOffset(String page) {
        int offset = page.indexOf("<ul class=\"pagination");
        if (offset == -1) {
            return super.preliminaryHtmlSuffixOffset(page);
        }
        return offset;
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("id=torrent><div class=panel-body");
        if (offset == -1) {
            return super.htmlPrefixOffset(html);
        }
        return offset;
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("Please leave comment");
        if (offset == -1) {
            return super.htmlSuffixOffset(html);
        }
        return offset;
    }

    public static void main(String[] args) throws Throwable {
        byte[] preliminaryResultsBytes = Files.readAllBytes(Paths.get("/Users/gubatron/Desktop/zooqle_preliminary.html"));
        String preliminaryResultsString = new String(preliminaryResultsBytes,"utf-8");
        Pattern preliminaryResultsPattern = Pattern.compile(PRELIMINARY_RESULTS_REGEX);
        Matcher preliminaryMatcher = preliminaryResultsPattern.matcher(preliminaryResultsString);
        int found = 0;
        while (preliminaryMatcher.find()) {
            found++;
            LOG.info("found " + found);
            LOG.info(preliminaryMatcher.group("detailPath")+".html");
        }


        byte[] htmlDetailsBytes = Files.readAllBytes(Paths.get("/Users/gubatron/Desktop/zooqle_detail.html"));
        String htmlDetailsString = new String(htmlDetailsBytes, "utf-8");
        Pattern htmlDetailPattern = Pattern.compile(HTML_DETAIL_REGEX);
        Matcher detailMatcher = htmlDetailPattern.matcher(htmlDetailsString);

        while (detailMatcher.find()) {
            System.out.println("filename: [" + detailMatcher.group("filename") + "]");
            System.out.println("seeds: [" + detailMatcher.group("seeds") + "]");
            System.out.println("infohash: [" + detailMatcher.group("infohash") + "]");
            System.out.println("torrent: [" + detailMatcher.group("torrent") + "]");
            System.out.println("size: [" + detailMatcher.group("size") + "]");
            System.out.println("sizeUnit: [" + detailMatcher.group("sizeUnit") + "]");
            System.out.print("creationtime: [");


            SearchMatcher sm = new SearchMatcher(detailMatcher);
            ZooqleSearchResult sr = new ZooqleSearchResult("https://zooqle.com/blabla.html", "http://zooqle.com", sm);
            System.out.println(sr.getCreationTime() + "]");
            System.out.println("size in bytes: [" + sr.getSize() + "]");
            System.out.println("===");
        }
        System.out.println("-done-");
    }
}
