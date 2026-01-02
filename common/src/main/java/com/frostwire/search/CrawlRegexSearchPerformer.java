/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
