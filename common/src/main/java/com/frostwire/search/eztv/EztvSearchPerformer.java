/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.search.eztv;

import com.frostwire.regex.Pattern;
import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class EztvSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(EztvSearchPerformer.class);

    private static Pattern searchPattern = null;

    private boolean isDDOSProtectionActive;

    public EztvSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 0);
        if (searchPattern == null) {
            searchPattern = Pattern.compile("(?is)<td class=\"forum_thread_post\">\\s+" + "<a href=\"(?<detailUrl>.*?)\" title=\"(?<displayname>.*?)\" alt=\".*?\" class=\"epinfo\".*?<a href=\"magnet(?<magnet>.*?)\" " + "class=\"magnet\".*?</td>.*?<td align=\"center\" class=\"forum_thread_post\">(?<size>[0-9\\. GMB]+)</td>" + ".*?<td align=\"center\" class=\"forum_thread_post\">(?<age>.*?)</td>");
        }

    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords;
    }

    @Override
    protected List<EztvSearchResult> searchPage(String page) {
        if (page == null || page.isEmpty() || !isValidHtml(page)) {
            return new ArrayList<>();
        }
        int startOffset = page.indexOf("Seeds");
        int endOffset = page.indexOf("<img src=\"//ezimg.ch/s/1/2/ssl.png");
        String reducedPage;

        if (startOffset > 0 && endOffset > 0 && endOffset > startOffset) {
            reducedPage = page.substring(startOffset, endOffset);
        } else {
            LOG.warn("EztvSearchPerformer()::searchPage() could not reduce page");
            reducedPage = page;
        }

        SearchMatcher matcher = new SearchMatcher(searchPattern.matcher(reducedPage));

        List<EztvSearchResult> results = new ArrayList<>();
        List<String> searchTokens = PerformersHelper.tokenizeSearchKeywords(getKeywords());
        int MAX_RESULTS = 75;
        int maxFailures = 10;
        boolean matcherFound = false;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                LOG.error("EztvSearchPerformer.searchPage() has failed.\n" + t.getMessage(), t);
                LOG.error("EztvSearchPerformer.searchPage() reduced page:\n" + reducedPage + "\n");
                //return results;
            }
            if (matcherFound) {
                try {
                    EztvSearchResult sr = new EztvSearchResult(getDomainName(), matcher);
                    if (PerformersHelper.someSearchTokensMatchSearchResult(searchTokens, sr)) {
                        results.add(sr);
                    }
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            } else {
                maxFailures--;
            }
        } while (maxFailures > 0 && !isStopped() && results.size() <= MAX_RESULTS);

        if (results.isEmpty()) {
            LOG.error("EztvSearchPerformer()::searchPage() no matches found for pattern");

            if (maxFailures == 0) {
                LOG.warn("EztvSearchPerformer search matcher broken on " + getDomainName() + ". Please notify at https://github.com/frostwire/frostwire/issues/new");
                LOG.warn("EztvSearchPerformer trying to search with regex: [" + searchPattern.toString() + "]");
            }
        }
        return results;
    }


    // EZTV is very simplistic in the search engine
    // just a simple keyword check allows to discard the page
    protected boolean isValidHtml(String html) {
        if (html == null || html.contains("Cloudflare Ray")) {
            isDDOSProtectionActive = true;
            return false;
        }
        return true;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return isDDOSProtectionActive;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
