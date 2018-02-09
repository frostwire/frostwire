/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

    private static final int MAX_RESULTS = 20;

    private static final String REGEX = "(?is)<td class=\"vertTh\">.*?<a href=\"[^\"]*?\" title=\"More from this category\">(.*?)</a>.*?</td>.*?<a href=\"([^\"]*?)\" class=\"detLink\" title=\"Details for ([^\"]*?)\">.*?</a>.*?<a href=\\\"(magnet:\\?xt=urn:btih:.*?)\\\" title=\\\"Download this torrent using magnet\\\">.*?</a>.*?<font class=\"detDesc\">Uploaded ([^,]*?), Size (.*?), ULed.*?<td align=\"right\">(.*?)</td>\\s*<td align=\"right\">(.*?)</td>";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    public TPBSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, MAX_RESULTS, MAX_RESULTS);
    }

    @Override
    public Pattern getPattern() {
        return PATTERN;
    }

    @Override
    public TPBSearchResult fromMatcher(SearchMatcher matcher) {
        return new TPBSearchResult(getDomainName(), matcher);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords + "/0/7/0";
    }

    @Override
    protected String getCrawlUrl(TPBSearchResult sr) {
        return sr.getTorrentUrl();
    }

    @Override
    protected List<? extends SearchResult> crawlResult(TPBSearchResult sr, byte[] data) throws Exception {
        return PerformersHelper.crawlTorrent(this, sr, data);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && html.indexOf("Cloudfare") == -1;
    }
}
