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

package com.frostwire.search.monova;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

import java.io.IOException;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class MonovaSearchPerformer extends TorrentRegexSearchPerformer<MonovaSearchResult> {

    private static final int MAX_RESULTS = 10;
    private static final String REGEX = "(?is)<a href=\"//%s/torrent/(?<itemid>[0-9]*?)/(?<filename>.*?).html";
    private static final String HTML_REGEX = "(?is).*?" +
            // filename
            "<li class=\"active\">(?<filename>.*?)</li>.*?" +
            // creationtime
            "<span>Added:</span>(?<creationtime>.*?)ago.*?" +
            // seeds
            "<span>Status:</span>(?<seeds>\\d+) seeders.*?" +
            // infohash
            "<span>Hash:</span>(?<infohash>[A-Fa-f0-9]{40}).*?</div>.*?" +
            // size
            "<span>Total size:</span>(?<size>.*?)</div>.*?" +
            "";

    public MonovaSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, String.format(REGEX,domainName), HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "http://"+getDomainName()+"/search.php?verified=1&sort=1&page=1&term=" + encodedKeywords;
    }

    @Override
    protected String fetchSearchPage(String url) throws IOException {
        return fetch(url, "MONOVA=1; MONOVA-ADULT=0; MONOVA-NON-ADULT=1;", null);
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String page) {
        return page.indexOf("<th class=\"torrent_name\">Torrent Name</th>");
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String fileName = matcher.group("filename").replace("&amp;","&");
        return new MonovaTempSearchResult(getDomainName(), matcher.group("itemid"), fileName);
    }

    @Override
    protected MonovaSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        MonovaSearchResult candidate = new MonovaSearchResult(sr.getDetailsUrl(), matcher);
        if (candidate.getSeeds() < 25 || candidate.getDaysOld() > 200) {
            //since we can only do monova using magnets, we better have seeds or else we'll
            //suck in UX.
            candidate = null;
        }
        return candidate;
    }

    /**
    public static void main(String[] args) throws Throwable {

        for (int i=1; i <= 2; i++) {
            byte[] readAllBytes = Files.readAllBytes(Paths.get("/Users/gubatron/Desktop/monova_input" + i + ".html"));
            String fileStr = new String(readAllBytes, "utf-8");
            System.out.println(HTML_REGEX);
            Pattern pattern = Pattern.compile(HTML_REGEX);
            Matcher matcher = pattern.matcher(fileStr);

            boolean matcherFind = matcher.find();
            System.out.println("find? : " + matcherFind);

            if (matcherFind) {
                System.out.println("group filename: [" + matcher.group("filename") + "]");
                System.out.println("group creationtime: [" + matcher.group("creationtime") + "]");
                System.out.println("group seeds: [" + matcher.group("seeds") + "]");
                System.out.println("group size: [" + matcher.group("size") + "]");
                System.out.println("group infohash: [" + matcher.group("infohash") + "]");
                System.out.println("\n========================");
            }
            System.out.println("");
        }
    }
    */
}
