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

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

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

    public static void main(String[] args) throws Throwable {
        /**
        byte[] readAllBytes = Files.readAllBytes(Paths.get("/Users/gubatron/Desktop/love2.html"));
        String fileStr = new String(readAllBytes,"utf-8");

        Pattern pattern = Pattern.compile(REGEX);
        //Pattern pattern = Pattern.compile(HTML_REGEX);
        
        Matcher matcher = pattern.matcher(fileStr);
        
        int found = 0;
        while (matcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            
            System.out.println("group 1: " + matcher.group(1));
            System.out.println("group 2: " + matcher.group(2));
            System.out.println("group 3: " + matcher.group(3));
            
            /**
            //test HTML_REGEX
            System.out.println("group 4: " + matcher.group(4));
            System.out.println("group 5: " + matcher.group(5));
            System.out.println("group 6: " + matcher.group(6));
            System.out.println("group 7: " + matcher.group(7));
            */
        /**
            System.out.println("===");
        }
        //System.out.println("-done-");
        */
    }
}