/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torlock;

import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexCrawlerSearchPerformer;
import com.frostwire.util.Logger;

/**
 * TorLock Search Performer for the TorLock torrent search engine.
 * 
 * Note: TorLock has moved from www.torlock.com to en.torlock-official.live
 * and is heavily protected by CloudFlare, which may prevent successful searches.
 * This search performer is disabled by default and should only be enabled
 * if you have confirmed that the site is accessible and working.
 * 
 * @author gubatron
 * @author aldenml
 */
public final class TorLockSearchPerformer extends TorrentRegexCrawlerSearchPerformer<TorLockSearchResult> {
    private static final Logger LOG = Logger.getLogger(TorLockSearchPerformer.class);
    private static final int MAX_RESULTS = 15;
    private static final String PRELIMINARY_REGEX = "(?is)<a href=/torrent/([0-9]*?/.*?\\.html)>";
    private static final String HTML_REGEX = "(?is)<a href=\".*?/tor/(?<torrentid>.*?).torrent\".*?" +
            "<dt>NAME</dt>.?<dd>(?<filename>.*?).torrent</dd>.*?" +
            "<dt>INFOHASH</dt><dd.*?>(?<infohash>.*?)</dd>.*?" +
            "<dt>SIZE</dt>.?<dd>(?<filesize>.*?) in.*?" +
            "<dt>ADDED</dt>.?<dd>Uploaded on (?<time>.*?) by.*?" +
            "<dt>SWARM</dt>.?<dd><b style=\"color:#FF5400\">(?<seeds>\\d*?)</b>";
    private boolean isDDOSProtectionActive;

    public TorLockSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, PRELIMINARY_REGEX, HTML_REGEX);
        LOG.warn("TorLock search performer is enabled but may not work due to CloudFlare protection. " +
                "Site has moved to " + domainName + " and is heavily protected.");
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/movies?keyword=" + encodedKeywords + "&quality=&genre=&rating=0&year=0&language=&order_by=latest";
    }

    @Override
    public Pattern getAltPattern() {
        return null;
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new TorLockTempSearchResult(getDomainName(), itemId);
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        // Look for common content start markers that might exist on the new site
        int offset = html.indexOf("SIGN UP</a>");
        if (offset == -1) {
            // Try alternative markers for the new site structure
            offset = html.indexOf("<main");
            if (offset == -1) {
                offset = html.indexOf("<div class=\"content");
                if (offset == -1) {
                    offset = html.indexOf("class=\"results");
                }
            }
        }
        return Math.max(offset, 0);
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        // Look for common content end markers
        int offset = html.indexOf(">Description</a></li>");
        if (offset == -1) {
            // Try alternative markers for the new site structure
            offset = html.indexOf("</main>");
            if (offset == -1) {
                offset = html.indexOf("<footer");
                if (offset == -1) {
                    offset = html.indexOf("</body>");
                }
            }
        }
        return Math.max(offset, 0);
    }

    @Override
    protected TorLockSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new TorLockSearchResult(getDomainName(), sr.getDetailsUrl(), matcher);
    }

    @Override
    protected boolean isValidHtml(String html) {
        if (html == null || html.isEmpty()) {
            isDDOSProtectionActive = true;
            LOG.warn("TorLock returned empty HTML response - site may be down or blocked");
            return false;
        }
        
        // Check for various CloudFlare protection indicators
        isDDOSProtectionActive = html.contains("Cloudflare") || 
                                html.contains("cloudflare") ||
                                html.contains("cf-browser-verification") ||
                                html.contains("cf-challenge-page") ||
                                html.contains("Checking your browser") ||
                                html.contains("DDoS protection") ||
                                html.contains("Ray ID") ||
                                html.contains("Security check");
        
        if (isDDOSProtectionActive) {
            LOG.warn("TorLock CloudFlare/DDoS protection detected - search will fail");
        }
        
        return !isDDOSProtectionActive;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return isDDOSProtectionActive;
    }

    @Override
    public boolean isCrawler() {
        return true;
    }
}
