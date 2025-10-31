/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for regex-based search pattern implementations.
 * Provides common regex matching logic for parsing HTML results.
 *
 * @author gubatron
 */
public abstract class RegexSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(RegexSearchPattern.class);
    private static final int DEFAULT_MAX_RESULTS = 100;

    protected final int maxResults;

    public RegexSearchPattern() {
        this(DEFAULT_MAX_RESULTS);
    }

    public RegexSearchPattern(int maxResults) {
        this.maxResults = maxResults;
    }

    /**
     * Returns the regex pattern to use for matching results.
     *
     * @return the compiled Pattern
     */
    protected abstract Pattern getPattern();

    /**
     * Creates a FileSearchResult from a regex match.
     *
     * @param matcher the search matcher containing the matched groups
     * @return a FileSearchResult or null if invalid
     */
    protected abstract FileSearchResult fromMatch(SearchMatcher matcher);

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            Pattern p = getPattern();
            Matcher m = p.matcher(responseBody);

            while (m.find() && results.size() < maxResults) {
                try {
                    FileSearchResult result = fromMatch(SearchMatcher.from(m));
                    if (result != null) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    LOG.warn("Error parsing search result: " + e.getMessage());
                    // Continue to next match
                }
            }
        } catch (Exception e) {
            LOG.error("Error executing regex pattern: " + e.getMessage(), e);
        }

        return results;
    }
}
