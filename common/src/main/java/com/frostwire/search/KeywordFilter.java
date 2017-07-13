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

import com.frostwire.licenses.Licenses;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class KeywordFilter {

    private final boolean inclusive;
    private final String keyword;
    private final String stringForm;
    private KeywordDetector.Feature feature;

    public KeywordFilter(boolean inclusive, String keyword, KeywordDetector.Feature feature) {
        this(inclusive, keyword, (String) null);
        this.feature = feature;
    }

    /**
     * NOTE: If you use this constructor, make sure the stringForm passed matches the inclusive and keyword
     * parameters. The constructor performs no validations and this could lead to unwanted behavior when
     * asking for toString(), as the stringForm will be the one returned by toString().
     *
     * @param inclusive  - the keyword should be included or not
     * @param keyword    - the keyword
     * @param stringForm - How this keyword filter was parsed out from a search
     */
    public KeywordFilter(boolean inclusive, String keyword, String stringForm) {
        this.inclusive = inclusive;
        this.keyword = keyword.toLowerCase();
        if (stringForm != null) {
            this.stringForm = stringForm;
        } else {
            this.stringForm = ((inclusive) ? "+" : "-") + ":keyword:" + this.keyword;
        }
        this.feature = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof KeywordFilter)) {
            return false;
        } else {
            KeywordFilter other = (KeywordFilter) obj;
            return inclusive == other.inclusive && keyword.equals(other.keyword);
        }
    }

    @Override
    public int hashCode() {
        return keyword.hashCode() * (inclusive ? 1 : -1);
    }

    public boolean isInclusive() {
        return inclusive;
    }

    public String getKeyword() {
        return keyword;
    }

    public KeywordDetector.Feature getFeature() {
        return this.feature;
    }

    @Override
    public String toString() {
        return stringForm;
    }

    public boolean accept(final String lowercaseHaystack) {
        boolean found = lowercaseHaystack.contains(keyword);
        return ((inclusive && found) || (!inclusive && !found));
    }

    static String getSearchResultHaystack(SearchResult sr) {
        StringBuilder queryString = new StringBuilder();
        if (sr.getSource() == null) {
            System.err.println("WARNING: " + sr.getClass().getSimpleName() + " has no source!");
        } else {
            queryString.append(sr.getSource());
            queryString.append(" ");
        }
        queryString.append(sr.getDisplayName());
        queryString.append(" ");
        if (sr instanceof FileSearchResult) {
            queryString.append(" ");
            queryString.append(((FileSearchResult) sr).getFilename());
        }
        queryString.append(" ");
        queryString.append(sr.getDetailsUrl());
        if (sr.getLicense() != Licenses.UNKNOWN) {
            queryString.append(sr.getLicense().getName());
        }
        return queryString.toString().toLowerCase();
    }

    public static boolean passesFilterPipeline(final SearchResult sr, final List<KeywordFilter> filterPipeline) {
        if (filterPipeline == null || filterPipeline.size() == 0) {
            return true;
        }
        String haystack = getSearchResultHaystack(sr);
        for (KeywordFilter filter : filterPipeline) {
            if (!filter.accept(haystack)) {
                return false;
            }
        }
        return true;
    }
}
