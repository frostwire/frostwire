/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

import com.frostwire.regex.Matcher;

/**
 * <strong>A memory conscious Matcher</strong><br/>
 * Instead of using the groups() that reference the original HTML strings,
 * we just make copies of those substrings with this search matcher everytime
 * we invoke group(), this way the original HTML can be dereferenced and garbage collected.
 *
 * @author gubatron
 * @author aldenml
 */
public final class SearchMatcher {
    private final Matcher matcher;

    public SearchMatcher(Matcher matcher) {
        this.matcher = matcher;
    }

    public static SearchMatcher from(Matcher matcher) {
        return new SearchMatcher(matcher);
    }

    public boolean find() {
        return matcher.find();
    }

    public String group(int group) {
        return copy(matcher.group(group));
    }

    public String group(String group) {
        if (matcher.hasGroup(group)) {
            return copy(matcher.group(group));
        }
        return null;
    }

    private String copy(String str) {
        if (str == null) {
            return null;
        }
        return new String(str.toCharArray());
    }
}
