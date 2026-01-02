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
