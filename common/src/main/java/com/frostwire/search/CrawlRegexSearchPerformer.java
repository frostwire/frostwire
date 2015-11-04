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

package com.frostwire.search;

import java.util.List;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class CrawlRegexSearchPerformer<T extends CrawlableSearchResult> extends CrawlPagedWebSearchPerformer<T> implements RegexSearchPerformer<T> {

    private final int regexMaxResults;

    public CrawlRegexSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls, int regexMaxResults) {
        super(domainName, token, keywords, timeout, pages, numCrawls);
        this.regexMaxResults = regexMaxResults;
    }

    @Override
    protected final List<? extends SearchResult> searchPage(String page) {
        String reducedPage = PerformersHelper.reduceHtml(page, preliminaryHtmlPrefixOffset(page), preliminaryHtmlSuffixOffset(page));
        return PerformersHelper.searchPageHelper(this, reducedPage, regexMaxResults);
    }

    protected int preliminaryHtmlSuffixOffset(String page) {
        return page.length();
    }

    protected int preliminaryHtmlPrefixOffset(String page) {
        return 0;
    }
}