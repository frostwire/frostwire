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

import com.frostwire.regex.Matcher;

/**
 * <strong>A memory conscious Matcher</strong><br/>
 * Instead of using the groups() that reference the original HTML strings,
 * we just make copies of those substrings with this search matcher everytime
 * we invoke group(), this way the original HTML can be dereferenced and garbage collected.
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class SearchMatcher {

    private final Matcher matcher;

    public static SearchMatcher from(Matcher matcher) {
        return new SearchMatcher(matcher);
    }
    
    private SearchMatcher(Matcher matcher) {
        this.matcher = matcher;
    }
    
    public boolean find() {
        return matcher.find();
    }

    public String group(int group) {
        return copy(matcher.group(group));
    }
    
    public String group(String group) {
        return copy(matcher.group(group));
    }
    
    private String copy(String str) {
        return new String(str.toCharArray());
    }
}
