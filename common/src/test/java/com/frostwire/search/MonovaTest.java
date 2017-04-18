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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * NOTE: To test from IntelliJ, open the 'common' project as a gradle project.
 * Build the gradle project and run the 'test' task.
 * @author gubatron
 * @author aldenml
 */
public class MonovaTest {

    private static final String testKeyword = "<some keyword here when you test>";

    @Test
    public void testRegex() throws IOException {
        MonovaSearchPerformer p = (MonovaSearchPerformer) SearchManager.MONOVA.newPerformer(0, testKeyword);

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
        MonovaSearchPerformer p = (MonovaSearchPerformer) SearchManager.MONOVA.newPerformer(0, testKeyword);

        String url = "<some monova page url when you test here>";
        String page = p.fetch(url);
        System.out.println(page);

        Pattern pattern = p.getDetailsPattern();
        SearchMatcher matcher = SearchMatcher.from(pattern.matcher(page));

        boolean matcher_found = matcher.find();
        if (matcher_found) {
            System.out.println("group filename: [" + matcher.group("filename") + "]");
            System.out.println("group creationtime: [" + matcher.group("creationtime") + "]");
            System.out.println("group infohash: [" + matcher.group("infohash") + "]");
            System.out.println("group size: [" + matcher.group("size") + "]");
            System.out.println("group torrenturl: [" + matcher.group("torrenturl") + "]");
        }
        Assert.assertTrue("No detail matched", matcher_found);
    }
}
