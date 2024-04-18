/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.tpb;

import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlRegexSearchPerformer;
import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class TPBSearchPerformer extends CrawlRegexSearchPerformer<TPBSearchResult> {
    public static String[] getMirrors() {
        return new String[]{
                "pirate-bay.info",
                "pirate-bays.net",
                "piratebay.live",
                "pirateproxylive.org",
                "thehiddenbay.com",
                "thepiratebay-unblocked.org",
                "thepiratebay.party",
                "thepiratebay.zone",
                "thepiratebay0.org",
                "thepiratebay10.org",
                "thepiratebay7.com",
                "tpb.party",
        };
    }

    private static final int MAX_RESULTS = 20;
    private static Pattern NEW_TPB_PATTERN;
    private static Pattern OLD_TPB_PATTERN;

    public TPBSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, MAX_RESULTS, MAX_RESULTS);
    }

    @Override
    public Pattern getPattern() {
        if (NEW_TPB_PATTERN == null) {
            // Simpler html output, no class=detLink, seems like the newer version of TPB which shows the upload date as well as the file size as a column
            // (?is)<td class=\"vertTh\">.*?<a href=\"[^\"]*?\" title=\"More from this category\">(.*?)</a>.*?</td>.*?<a href=\"(?<detailsUrl>[^\"]*?)\" title=\"Details for (?<filename>[^\"]*?)\">.*?<td>(?<creationTime>.*?)</td>.*?<td><nobr><a href=\"magnet(?<magnet>.*?)\" title=\"Download this torrent using magnet\">.*?<td align=\"right\">(?<size>.*?)</td>.*?<td align=\"right\">(?<seeds>.*?)</td>.*?<td align=\"right\">(?<leeches>.*?)</td>.*?</tr>
            NEW_TPB_PATTERN = Pattern.compile("(?is)<td class=\\\"vertTh\\\">.*?<a href=\\\"[^\\\"]*?\\\" title=\\\"More from this category\\\">(.*?)</a>.*?</td>.*?<a href=\\\"(?<detailsUrl>[^\\\"]*?)\\\" title=\\\"Details for (?<filename>[^\\\"]*?)\\\">.*?<td>(?<creationTime>.*?)</td>.*?<td><nobr><a href=\\\"magnet(?<magnet>.*?)\\\" title=\\\"Download this torrent using magnet\\\">.*?<td align=\\\"right\\\">(?<size>.*?)</td>.*?<td align=\\\"right\\\">(?<seeds>.*?)</td>.*?<td align=\\\"right\\\">(?<leeches>.*?)</td>.*?</tr>");
        }
        return NEW_TPB_PATTERN;
    }

    @Override
    public Pattern getAltPattern() {
        if (OLD_TPB_PATTERN == null) {
            // Old html output matching
            // (?is)<td class=\"vertTh\">.*?<a href=\"[^\"]*?\" title=\"More from this category\">(.*?)</a>.*?</td>.*?<a href=\"(?<detailsUrl>[^\"]*?)\" class=\"detLink\" title=\"Details for (?<filename>[^\"]*?)\">.*?</a>.*?</div>.*?<a href=\"magnet(?<magnet>.*?)\" title=\"Download this torrent using magnet\">.*?Uploaded (?<creationTime>[^,]*?), Size (?<size>.*?), ULed by.*?<td align=\"right\">(?<seeds>.*?)</td>.*?</tr>
            OLD_TPB_PATTERN = Pattern.compile("(?is)<td class=\\\"vertTh\\\">.*?<a href=\\\"[^\\\"]*?\\\" title=\\\"More from this category\\\">(.*?)</a>.*?</td>.*?<a href=\\\"(?<detailsUrl>[^\\\"]*?)\\\" class=\\\"detLink\\\" title=\\\"Details for (?<filename>[^\\\"]*?)\\\">.*?</a>.*?</div>.*?<a href=\\\"magnet(?<magnet>.*?)\\\" title=\\\"Download this torrent using magnet\\\">.*?Uploaded (?<creationTime>[^,]*?), Size (?<size>.*?), ULed by.*?<td align=\\\"right\\\">(?<seeds>.*?)</td>.*?</tr>");
        }
        return OLD_TPB_PATTERN;
    }

    @Override
    public TPBSearchResult fromMatcher(SearchMatcher matcher) {
        return new TPBSearchResult(matcher);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords + "/0/7/0";
    }

    @Override
    protected String getCrawlUrl(TPBSearchResult sr) {
        return sr.getTorrentUrl();
    }

    @Override
    protected List<? extends SearchResult> crawlResult(TPBSearchResult sr, byte[] data) {
        return PerformersHelper.crawlTorrentInfo(this, sr, data);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }

    @Override
    public boolean isCrawler() {
        /** This search performer isn't really a detail-page HTTP crawler
         * It's however able to download magnets or .torrents and crawl the contents inside the Torrent Info
         * to populate the search results with more info.
         * */
        return false;
    }
}
