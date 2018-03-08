/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.

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

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

public class KeywordFilterTest {

    public static boolean passesFilterPipeline(final SearchResult sr, final List<KeywordFilter> filterPipeline) {
        if (filterPipeline == null || filterPipeline.size() == 0) {
            return true;
        }
        String haystack = KeywordFilter.getSearchResultHaystack(sr);
        for (KeywordFilter filter : filterPipeline) {
            if (!filter.accept(haystack)) {
                return false;
            }
        }
        return true;
    }

    public static String cleanQuery(String query, List<KeywordFilter> keywordFilters) {
        for (KeywordFilter filter : keywordFilters) {
            query = query.replace(filter.toString(), "");
        }
        return query.trim();
    }

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
        KeywordFilter MITfilter = new KeywordFilter(true, "MIT", (KeywordDetector.Feature) null);
        if (!assertTrue("'MIT' keyword inclusion test", MITfilter.accept(haystack))) {
            return false;
        }

        KeywordFilter notthereFilter = new KeywordFilter(true, "notthere", (KeywordDetector.Feature) null);
        if (!assertFalse("'notthere' keyword inclusion fail test", notthereFilter.accept(haystack))) {
            return false;
        }

        KeywordFilter athensFilter = new KeywordFilter(true, "athens", (KeywordDetector.Feature) null);
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
        //noinspection RedundantIfStatement
        if (!assertFalse("inclusion pipeline fail test", KeywordFilter.passesFilterPipeline(fsr, failPipeline))) {
            return false;
        }
        return true;
    }

    private static boolean testExclusiveFilters() {
        final String haystack = KeywordFilter.getSearchResultHaystack(fsr);
        KeywordFilter MITfilter = new KeywordFilter(false, "MIT", (KeywordDetector.Feature) null);
        if (!assertFalse("'MIT' keyword exclusion fail test", MITfilter.accept(haystack))) {
            return false;
        }

        KeywordFilter notthereFilter = new KeywordFilter(false, "notthere", (KeywordDetector.Feature) null);
        if (!assertTrue("'notthere' keyword exclusion test", notthereFilter.accept(haystack))) {
            return false;
        }

        KeywordFilter frostwireFilter = new KeywordFilter(false, "frostwire", (KeywordDetector.Feature) null);
        if (!assertTrue("'frostwire' keyword exclusion test", notthereFilter.accept(haystack))) {
            return false;
        }

        List<KeywordFilter> acceptablePipeline = new LinkedList<>();
        acceptablePipeline.add(notthereFilter);
        acceptablePipeline.add(frostwireFilter);
        if (!assertTrue("exclusion pipeline test", passesFilterPipeline(fsr,acceptablePipeline))) {
            return false;
        }

        KeywordFilter athensFilter = new KeywordFilter(false, "athens", (KeywordDetector.Feature) null);
        if (!assertFalse("'athens' exclusion fail test", athensFilter.accept(haystack))) {
            return false;
        }

        List<KeywordFilter> failPipeline = new LinkedList<>();
        failPipeline.add(frostwireFilter);
        failPipeline.add(athensFilter);
        failPipeline.add(MITfilter);
        failPipeline.add(notthereFilter);
        //noinspection RedundantIfStatement
        if (!assertFalse("exclusion pipeline fail test", passesFilterPipeline(fsr, failPipeline))) {
            return false;
        }
        return true;
    }

    private static boolean testMixedFilters() {
        KeywordFilter MITfilter = new KeywordFilter(true, "MIT", (KeywordDetector.Feature) null);
        KeywordFilter frostwireExclusionFilter = new KeywordFilter(false, "frostwire", (KeywordDetector.Feature) null);
        KeywordFilter athensFilter = new KeywordFilter(true, "athens", (KeywordDetector.Feature) null);
        List<KeywordFilter> mixedPipeline = new LinkedList<>();
        mixedPipeline.add(MITfilter);
        mixedPipeline.add(frostwireExclusionFilter);
        mixedPipeline.add(athensFilter);
        //noinspection RedundantIfStatement
        if (!assertTrue("mixed pipeline test", passesFilterPipeline(fsr, mixedPipeline))) {
            return false;
        }
        return true;
    }

    private static boolean testParseKeywordFilters() {
        //parseKeywordFilters
        /*
        List<KeywordFilter> keywordFilters = KeywordFilter.parseKeywordFilters("yaba daba doo +:keyword:thein -:keyword:theout +:keyward:notamatch :keyword:home");
        if (!assertTrue("parse keywords detection test 1", keywordFilters.size() == 3)) return false;
        if (!assertTrue("parse keywords detection test 2", keywordFilters.get(0).inclusive)) return false;
        if (!assertFalse("parse keywords detection test 3",keywordFilters.get(1).inclusive)) return false;
        if (!assertTrue("parse keywords detection test 4", keywordFilters.get(2).inclusive)) return false;
        if (!assertTrue("parse keywords detection test 5", keywordFilters.get(2).keyword.equals("home"))) return false;
        if (!assertTrue("toString() test 1", keywordFilters.get(0).toString().equals("+:keyword:thein"))) return false;
        if (!assertTrue("toString() test 2", keywordFilters.get(1).toString().equals("-:keyword:theout"))) return false;
        //noinspection RedundantIfStatement
        if (!assertTrue("toString() test 3", keywordFilters.get(2).toString().equals(":keyword:home"))) return false;
        */
        return true;
    }

    private static boolean testConstructors() {
        /*
        KeywordFilter f = new KeywordFilter(true, "wisdom", (KeywordDetector.Feature) null);
        if (!assertTrue("constructor test 1", f.inclusive)) return false;
        if (!assertTrue("constructor test 2", f.keyword.equals("wisdom"))) return false;
        if (!assertTrue("constructor test 3", f.toString().equals("+:keyword:wisdom"))) return false;
        f = new KeywordFilter(false, "patience", (KeywordDetector.Feature) null);
        if (!assertFalse("constructor test 4", f.inclusive)) return false;
        if (!assertTrue("constructor test 5", f.keyword.equals("patience"))) return false;
        if (!assertTrue("constructor test 6", f.toString().equals("-:keyword:patience"))) return false;
        f = new KeywordFilter(true, "love", ":keyword:love");
        if (!assertTrue("constructor test 7", f.inclusive)) return false;
        if (!assertTrue("constructor test 8", f.keyword.equals("love"))) return false;
        if (!assertFalse("constructor test 9", f.toString().equals("+:keyword:love"))) return false;
        //noinspection RedundantIfStatement
        if (!assertTrue("constructor test 9", f.toString().equals(":keyword:love"))) return false;
        */
        return true;
    }

    private static boolean testCleanQuery() {
        /*
        String query = "I know it is wet and the sun is not sunny, but we can have lots of good fun that is funny :keyword:somesource -:keyword:mp4 +:keyword:pdf";
        List<KeywordFilter> keywordFilters = KeywordFilter.parseKeywordFilters(query);
        if (!assertTrue("test cleanQuery 1", keywordFilters.size() == 3)) return false;
        String cleaned = cleanQuery(query, keywordFilters);
        //noinspection RedundantIfStatement
        if (!assertTrue("test cleanQuery 2",
                cleaned.equals("I know it is wet and the sun is not sunny, but we can have lots of good fun that is funny")))
            return false;*/
        return true;
    }

    public static void main(String[] args) {
        if (!testInclusiveFilters()) return;
        if (!testExclusiveFilters()) return;
        if (!testMixedFilters()) return;
        if (!testParseKeywordFilters()) return;
        if (!testConstructors()) return;
        if (!testCleanQuery()) return;
        System.out.println("PASSED ALL TESTS");
    }
}
