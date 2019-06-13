/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.NameHolder;

import java.util.HashSet;

/**
 * @author gubatron
 * @author aldenml
 */
final class SearchResultNameHolder extends NameHolder {
    private final String displayName;

    SearchResultNameHolder(final UISearchResult sr) {
        super(buildHTMLString(sr));
        this.displayName = sr.getDisplayName();
    }
//    public int compareTo(SearchResultNameHolder o) {
//        return AbstractTableMediator.compare(sr.getDisplayName(), o.sr.getDisplayName());
//    }

    private static String buildHTMLString(UISearchResult sr) {
        return "<html><div width=\"1000000px\">" + simpleHighlighter(sr.getQuery(), sr.getDisplayName()) + "</div></html>";
    }

    private static String simpleHighlighter(String query, String str) {
        if (query == null || query.isEmpty()) {
            return str;
        }
        // Get unique tokens in the query
        String[] rawTokens = query.split("\\s+");
        HashSet<String> uniqueTokens = new HashSet<>();
        for (String token : rawTokens) {
            uniqueTokens.add(token.trim());
        }
        // Replace all instances of unique tokens for bolded versions in original string
        for (String token : uniqueTokens) {
            // find case insensitive regions where our token could be
            int offset = 0;
            int regionEnd = offset + token.length();
            while (regionEnd < str.length()) {
                if (str.regionMatches(true, offset, token, 0, token.length())) {
                    String tokenAsFoundInStr = str.substring(offset, offset + token.length());
                    str = str.substring(0, offset) + "<b>" + tokenAsFoundInStr + "</b>" +
                            str.substring(offset + token.length());
                    offset += token.length() + 7;
                } else {
                    offset += 1;
                }
                regionEnd = offset + token.length();
            }
        }
        return str;
    }

    public String toString() {
        return displayName;
    }
}
