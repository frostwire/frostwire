/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.filters;

import com.limegroup.gnutella.settings.FilterSettings;

import java.util.Vector;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchFilterFactoryImpl implements SearchFilterFactory {
    public SearchFilterFactoryImpl() {
    }

    /**
     * Returns a composite filter of the given filters.
     *
     * @param filters a Vector of SpamFilter.
     */
    private static SearchFilter compose(Vector<? extends SearchFilter> filters) {
        //As a minor optimization, we avoid a few method calls in
        //special cases.
        if (filters.size() == 0) {
            return new AllowFilter();
        } else if (filters.size() == 1) {
            return filters.get(0);
        } else {
            SearchFilter[] delegates = new SearchFilter[filters.size()];
            filters.copyInto(delegates);
            return new CompositeFilter(delegates);
        }
    }

    public SearchFilter createFilter() {
        Vector<SearchFilter> buf = new Vector<>();
        String[] badWords = FilterSettings.BANNED_WORDS.getValue();
        boolean filterAdult = FilterSettings.FILTER_ADULT.getValue();
        if (badWords.length != 0 || filterAdult) {
            KeywordFilter kf = new KeywordFilter();
            for (String badWord : badWords) kf.disallow(badWord);
            if (filterAdult)
                kf.disallowAdult();
            buf.add(kf);
        }
        return compose(buf);
    }
}