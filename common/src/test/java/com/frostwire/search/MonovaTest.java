/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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

import com.frostwire.regex.Pattern;
import com.frostwire.search.monova.MonovaSearchPerformer;
import org.junit.Test;

import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public class MonovaTest {

    @Test
    public void testRegex() throws IOException {
        MonovaSearchPerformer p = (MonovaSearchPerformer) SearchManager.MONOVA.newPerformer(0, "<keyword>");

        String url = p.getUrl(0, p.getEncodedKeywords());
        System.out.println(url);
        String page = p.fetchSearchPage(url);
        System.out.println(page);

        Pattern pattern = p.getPattern();
        SearchMatcher matcher = SearchMatcher.from(pattern.matcher(page));

        while (matcher.find()) {
            System.out.println("itemid: [" + matcher.group("itemid") + "]");
        }
    }

    @Test
    public void testDetailsRegex() throws IOException {
        MonovaSearchPerformer p = (MonovaSearchPerformer) SearchManager.MONOVA.newPerformer(0, "<keyword>");

        String url = "<url>";
        String page = p.fetch(url);
        System.out.println(page);

        Pattern pattern = p.getDetailsPattern();
        SearchMatcher matcher = SearchMatcher.from(pattern.matcher(page));

        if (matcher.find()) {
            System.out.println("group filename: [" + matcher.group("filename") + "]");
            System.out.println("group creationtime: [" + matcher.group("creationtime") + "]");
            System.out.println("group seeds: [" + matcher.group("seeds") + "]");
            System.out.println("group infohash: [" + matcher.group("infohash") + "]");
            System.out.println("group size: [" + matcher.group("size") + "]");
        } else {
            System.out.println("No detail matched.");
        }
    }
}
