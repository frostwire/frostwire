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

import com.frostwire.logging.Logger;
import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class EztvSearchPerformer extends TorrentRegexSearchPerformer<EztvSearchResult> {
    private static Logger LOG = Logger.getLogger(EztvSearchPerformer.class);
    private static final int MAX_RESULTS = 20;
    private static final String REGEX = "(?is)<a href=\"(/ep/.*?)\"";
    private static String DYNAMIC_TRASH_CHECK_STRING = null;

    // This is a good example of optional regex groups when a page might have different possible formats to parse.
    private static final String HTML_REGEX =
            "(?is)<td class=\"section_post_header\" colspan=\"2\"><h1><span style.*?>(?<displaynamefallback>.*?)</span></h1></td>.*?"+
            "Download Links.*?"+
            ".*<a href=\"(?<magneturl>magnet:\\?.*?)\" class=\"magnet\".*?"+
            //"(<a href=\"(?<magneturl>magnet:\\?.*?)\" title=\"Magnet Link\".*?)?"+
            ".*<a href=\"(?<torrenturl>http(s)?.*?\\.torrent)\" class=\"download_.\".*?"+
            "(Torrent Info.*?title=\"(?<displayname>.*?)\".*?)?"+
            "(<b>Torrent File:</b>\\s+(?<displayname2>.*?)<br.*?)?"+
            "(<b>Torrent Hash:</b>\\s+(?<infohash>.*?)<br.*?)?" +
            "<b>Filesize:</b>\\s+(?<filesize>.*?)<br.*?"+
            "<b>Released:</b>\\s+(?<creationtime>.*?)<br";

    public EztvSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
        populateDynamicTrashChecker(domainName);
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new EztvTempSearchResult(getDomainName(),itemId);
    }

    @Override
    protected String fetchSearchPage(String url) {
        Map<String, String> formData = new HashMap<>();
        formData.put("SearchString1", getEncodedKeywords());
        formData.put("SearchString", "");
        formData.put("search", "Search");
        return post(url, formData);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://"+getDomainName()+"/search/";
    }

    @Override
    protected EztvSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new EztvSearchResult(sr.getDetailsUrl(), matcher);
    }

    protected int preliminaryHtmlPrefixOffset(String html) {
        //LOG.info("calling preliminaryHtmlPrefixOffset");
        int fallbackOffset = fallbackPreliminaryHtmlOffset(html);
        int offset;
        if (EztvSearchPerformer.DYNAMIC_TRASH_CHECK_STRING == null) {
            //LOG.info("Don't have trash to look for :(");
            offset = fallbackOffset;
        } else {
            //LOG.info("looking for trash ["+ DYNAMIC_TRASH_CHECK_STRING +"] in:\n" + html + "\n\n");
            offset = html.indexOf(DYNAMIC_TRASH_CHECK_STRING);
            // no trash found
            if (offset == -1) {
                //LOG.info("Didn't Find Trash!");
                offset = fallbackOffset;
            } else {
                //LOG.info("Found Trash at "+ offset + "!");
                offset += 256;
            }
        }
        return offset;
    }

    private int fallbackPreliminaryHtmlOffset(String html) {
        int offset = html.indexOf("id=\"searchsearch_submit\"");
        offset = offset > 0 ? offset : 0;
        return offset;
    }

    /**
     *
     * EZTV tends to return place holder search results containing the latest
     * additions to their index when there are no search results. This makes
     * the FrostWire experience somewhat confusing since a lot of unrelated results
     * show up.
     * We can find these items on their home page. If we can find the last search result
     * of their homepage among a list of search results, there's a really good chance
     * we have irrelevant search results on our hands.
     */
    private void populateDynamicTrashChecker(String domainName) {
        if (EztvSearchPerformer.DYNAMIC_TRASH_CHECK_STRING == null || EztvSearchPerformer.DYNAMIC_TRASH_CHECK_STRING.isEmpty()) {
            final HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            try {
                final byte[] bytes = client.getBytes("https://" + domainName, 5000);
                if (bytes !=null) {
                    final Pattern pattern = Pattern.compile(REGEX);
                    final Matcher matcher = pattern.matcher(new String(bytes));
                    String lastGroupFound = null;
                    while (matcher.find()) {
                        lastGroupFound = matcher.group(1);
                    }
                    if (lastGroupFound != null) {
                        EztvSearchPerformer.DYNAMIC_TRASH_CHECK_STRING = lastGroupFound;
                        //LOG.info("Using the following as TRASH:");
                        //LOG.info(EztvSearchPerformer.DYNAMIC_TRASH_CHECK_STRING);
                    }
                }
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
        }
    }

    /**
    public static void main(String[] args) throws Throwable {
        
        byte[] readAllBytes = Files.readAllBytes(Paths.get("/Users/gubatron/Desktop/eztv5.html"));
        String fileStr = new String(readAllBytes,"utf-8");

        //Pattern pattern = Pattern.compile(REGEX);
        Pattern pattern = Pattern.compile(HTML_REGEX);
        
        Matcher matcher = pattern.matcher(fileStr);
        
        int found = 0;
        while (matcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("magneturl: [" + matcher.group("magneturl") + "]");
            System.out.println("torrenturl: [" + matcher.group("torrenturl") + "]");
            System.out.println("displayname: [" + matcher.group("displayname") + "]");
            System.out.println("displayname2: [" + matcher.group("displayname2") + "]");
            System.out.println("displaynamefallback: [" + matcher.group("displaynamefallback") + "]");
            System.out.println("infohash: [" + matcher.group("infohash") + "]");
            System.out.println("filesize: [" + matcher.group("filesize") + "]");
            System.out.println("creationtime: [" + matcher.group("creationtime") + "]");
            System.out.println("===");

            SearchMatcher sm = new SearchMatcher(matcher);
            EztvSearchResult sr = new EztvSearchResult("http://someurl.com", sm);
        }
        System.out.println("-done-");
    }
    */
}