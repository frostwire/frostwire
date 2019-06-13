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
