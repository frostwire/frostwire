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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import com.limegroup.gnutella.gui.tables.NameHolder;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public final class SearchResultNameHolder extends NameHolder {

    private final UISearchResult sr;
    private final String displayName;

    public SearchResultNameHolder(final UISearchResult sr) {
        super(buildHTMLString(sr));
        this.sr = sr;
        this.displayName = sr.getDisplayName();
    }
    

    public int compareTo(SearchResultNameHolder o) {
        return AbstractTableMediator.compare(sr.getDisplayName(), o.sr.getDisplayName());
    }

    public String toString() {
        return displayName;
    }
    
    private static String buildHTMLString(UISearchResult sr) {
        return "<html><div width=\"1000000px\">" + simpleHighlighter(sr.getQuery(), sr.getDisplayName()) + "</div></html>";
    }

    private static String simpleHighlighter(String query, String str) {
        if (!query.isEmpty()) {
            for (String token : query.split("\\s+")) {
                StringBuilder sb = new StringBuilder(2 * str.length());
                for (int i = 0; i < str.length();) {
                    if (i + token.length() <= str.length()) {
                        String s = str.substring(i, token.length() + i);
                        if (s.equalsIgnoreCase(token)) {
                            sb.append("<b>" + s + "</b>");
                            i += s.length();
                        } else {
                            sb.append(str.charAt(i));
                            i++;
                        }
                    } else {
                        sb.append(str.charAt(i));
                        i++;
                    }
                }
                str = sb.toString();
            }
        }
        return str;
    }
}
