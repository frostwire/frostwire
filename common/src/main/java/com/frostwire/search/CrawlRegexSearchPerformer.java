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

package com.frostwire.search;

import java.util.Collections;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class CrawlRegexSearchPerformer<T extends CrawlableSearchResult> extends CrawlPagedWebSearchPerformer<T> implements RegexSearchPerformer<T> {
    private final int regexMaxResults;

    public CrawlRegexSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls, int regexMaxResults) {
        super(domainName, token, keywords, timeout, pages, numCrawls);
        this.regexMaxResults = regexMaxResults;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        if (!isValidHtml(page)) {
            return Collections.emptyList();
        }
        int prefixOffset = preliminaryHtmlPrefixOffset(page);
        int suffixOffset = preliminaryHtmlSuffixOffset(page);
        String reducedPage = PerformersHelper.reduceHtml(page, prefixOffset, suffixOffset);
        return PerformersHelper.searchPageHelper(this, reducedPage, regexMaxResults);
    }

    /**
     * Give the opportunity to an implementor to specify if the unreduced HTML
     * that is about to be crawled is a valid one, and not report errors when
     * there is none.
     *
     * @param html the unreduced html
     * @return {@code true} is valid and allowed to be processed, {@code false}
     * otherwise.
     */
    abstract protected boolean isValidHtml(String html);

    protected int preliminaryHtmlSuffixOffset(String page) {
        return page.length();
    }

    protected int preliminaryHtmlPrefixOffset(String page) {
        return 0;
    }
}
