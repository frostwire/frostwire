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
public final class KeywordFilter {
    private final boolean inclusive;
    private final String keyword;
    private KeywordDetector.Feature feature;

    public KeywordFilter(boolean inclusive, String keyword, KeywordDetector.Feature feature) {
        this.inclusive = inclusive;
        this.keyword = keyword.toLowerCase();
        this.feature = feature;
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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KeywordFilter)) {
            return false;
        }
        KeywordFilter other = (KeywordFilter) obj;
        return inclusive == other.inclusive && keyword.equals(other.keyword);
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
        return feature;
    }

    public KeywordFilter negate() {
        return new KeywordFilter(!inclusive, keyword, feature);
    }

    public boolean accept(final String lowercaseHaystack) {
        boolean found = lowercaseHaystack.contains(keyword);
        return ((inclusive && found) || (!inclusive && !found));
    }
}
