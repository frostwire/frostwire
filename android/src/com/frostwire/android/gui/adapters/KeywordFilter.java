/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters;

import android.support.annotation.NonNull;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created on 11/24/16.
 *
 * @author gubatron
 * @author aldenml
 */
public class KeywordFilter {
    private final boolean inclusive;
    private final String keyword;

    public KeywordFilter(boolean inclusive, String keyword) {
        this.inclusive = inclusive;
        this.keyword = keyword.toLowerCase();

    }

    public boolean accept(@NonNull final String lowercaseHaystack) {
        boolean found = lowercaseHaystack.contains(keyword);
        return ((inclusive && found) || (!inclusive && !found));
    }

    private static String getSearchResultHaystack(@NonNull SearchResult sr) {
        StringBuilder queryString = new StringBuilder();
        queryString.append(sr.getDisplayName());
        queryString.append(" ");
        queryString.append(sr.getSource());
        if (sr instanceof FileSearchResult) {
            queryString.append(" ");
            queryString.append(((FileSearchResult) sr).getFilename());
        }
        queryString.append(" ");
        queryString.append(sr.getDetailsUrl());
        queryString.append(" ");
        queryString.append(sr.getThumbnailUrl());
        if (sr.getLicense() != Licenses.UNKNOWN) {
            queryString.append(sr.getLicense().getName());
        }
        return queryString.toString().toLowerCase();
    }

    public static boolean passesFilterPipeline(@NonNull final SearchResult sr, @NonNull final List<KeywordFilter> filterPipeline) {
        boolean accepted = true;
        String haystack = getSearchResultHaystack(sr);
        Iterator<KeywordFilter> it = filterPipeline.iterator();
        while (accepted && it.hasNext()) {
            KeywordFilter filter = it.next();
            accepted &= filter.accept(haystack);
            System.out.println("KeywordFilter.passesFilterPipeline (pipe = " + filter.keyword + ")");
        }
        return accepted;
    }

    private static class KeywordFilterTests {
        private static final FileSearchResult fsr = new FileSearchResult() {
            @Override
            public String getFilename() {
                return "timon_of_athens.txt";
            }

            @Override
            public long getSize() {
                return 0;
            }

            @Override
            public String getDisplayName() {
                return "Timon of Athens";
            }

            @Override
            public String getDetailsUrl() {
                return "http://shakespeare.mit.edu/timon/timon.4.1.html";
            }

            @Override
            public long getCreationTime() {
                return 0;
            }

            @Override
            public String getSource() {
                return "MIT";
            }

            @Override
            public License getLicense() {
                return Licenses.PUBLIC_DOMAIN_MARK;
            }

            @Override
            public String getThumbnailUrl() {
                return "Let me look back upon thee. O thou wall, That girdlest in those wolves, dive in the earth";
            }

            @Override
            public int uid() {
                return 0;
            }
        };

        // Poor man's JUnit, Temporary until we formalize unit tests in Android
        // I tried with compileTest '...junit' on build.gradle but failed.
        // just needed to get simple tests going.

        public static boolean assertTrue(String description, boolean result) {
            PrintStream ops = result ? System.out : System.err;
            ops.println((result ? "PASSED" : "FAILED") + " [" + description + "]");
            return result;
        }

        public static boolean assertFalse(String description, boolean result) {
            PrintStream ops = !result ? System.out : System.err;
            ops.println((!result ? "PASSED" : "FAILED") + " [" + description + "]");
            return !result;
        }

        private static boolean testInclusiveFilters() {
            final String haystack = KeywordFilter.getSearchResultHaystack(fsr);
            KeywordFilter MITfilter = new KeywordFilter(true, "MIT");
            if (!assertTrue("'MIT' keyword inclusion test", MITfilter.accept(haystack))) {
                return false;
            }

            KeywordFilter notthereFilter = new KeywordFilter(true, "notthere");
            if (!assertFalse("'notthere' keyword inclusion fail test", notthereFilter.accept(haystack))) {
                return false;
            }

            KeywordFilter athensFilter = new KeywordFilter(true, "athens");
            if (!assertTrue("'athens' keyword inclusion test", athensFilter.accept(haystack))) {
                return false;
            }

            List<KeywordFilter> acceptablePipeline = new LinkedList<>();
            acceptablePipeline.add(MITfilter);
            acceptablePipeline.add(athensFilter);
            if (!assertTrue("inclusion pipeline test", KeywordFilter.passesFilterPipeline(fsr, acceptablePipeline))) {
                return false;
            }

            List<KeywordFilter> failPipeline = new LinkedList<>();
            failPipeline.add(MITfilter);
            failPipeline.add(notthereFilter);
            failPipeline.add(athensFilter); // this one shouldn't even be checked as it should short circuit
            if (!assertFalse("inclusion pipeline fail test", KeywordFilter.passesFilterPipeline(fsr, failPipeline))) {
                return false;
            }
            return true;
        }

        private static boolean testExclusiveFilters() {
            final String haystack = KeywordFilter.getSearchResultHaystack(fsr);
            KeywordFilter MITfilter = new KeywordFilter(false, "MIT");
            if (!assertFalse("'MIT' keyword exclusion fail test", MITfilter.accept(haystack))) {
                return false;
            }

            KeywordFilter notthereFilter = new KeywordFilter(false, "notthere");
            if (!assertTrue("'notthere' keyword exclusion test", notthereFilter.accept(haystack))) {
                return false;
            }

            KeywordFilter frostwireFilter = new KeywordFilter(false, "frostwire");
            if (!assertTrue("'frostwire' keyword exclusion test", notthereFilter.accept(haystack))) {
                return false;
            }

            List<KeywordFilter> acceptablePipeline = new LinkedList<>();
            acceptablePipeline.add(notthereFilter);
            acceptablePipeline.add(frostwireFilter);
            if (!assertTrue("exclusion pipeline test", passesFilterPipeline(fsr,acceptablePipeline))) {
               return false;
            }

            KeywordFilter athensFilter = new KeywordFilter(false, "athens");
            if (!assertFalse("'athens' exclusion fail test", athensFilter.accept(haystack))) {
                return false;
            }

            List<KeywordFilter> failPipeline = new LinkedList<>();
            failPipeline.add(frostwireFilter);
            failPipeline.add(athensFilter);
            failPipeline.add(MITfilter);
            failPipeline.add(notthereFilter);
            if (!assertFalse("exclusion pipeline fail test", passesFilterPipeline(fsr, failPipeline))) {
                return false;
            }
            return true;
        }

        private static boolean testMixedFilters() {
            final String haystack = KeywordFilter.getSearchResultHaystack(fsr);
            KeywordFilter MITfilter = new KeywordFilter(true, "MIT");
            KeywordFilter frostwireExclusionFilter = new KeywordFilter(false, "frostwire");
            KeywordFilter athensFilter = new KeywordFilter(true, "athens");
            List<KeywordFilter> mixedPipeline = new LinkedList<>();
            mixedPipeline.add(MITfilter);
            mixedPipeline.add(frostwireExclusionFilter);
            mixedPipeline.add(athensFilter);
            if (!assertTrue("mixed pipeline test", passesFilterPipeline(fsr, mixedPipeline))) {
                return false;
            }
            return true;
        }

        public static void main(String[] args) {
            if (!KeywordFilterTests.testInclusiveFilters()) {
                return;
            }
            if (!KeywordFilterTests.testExclusiveFilters()) {
                return;
            }
            if (!KeywordFilterTests.testMixedFilters()) {
                return;
            }

            System.out.println("PASSED ALL TESTS");
        }
    }
}
