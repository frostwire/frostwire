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
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import org.junit.Test;

import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public class LimeTorrentsTest {

    @Test
    public void testDetailsRegex() throws IOException {
        /*
        Torrentz2SearchPerformer p = (Torrentz2SearchPerformer) SearchManager.LIMETORRENTS.newPerformer(0, "<keyword>");

        String url = "<url>";
        String page = p.fetch(url);
        System.out.println(page);

        Pattern pattern = p.getDetailsPattern();
        SearchMatcher matcher = SearchMatcher.from(pattern.matcher(page));

        if (matcher.find()) {
            System.out.println("File name: " + matcher.group("filename"));
            System.out.println("TorrentID: " + matcher.group("torrentid"));
            System.out.println("Size: " + matcher.group("filesize"));
            System.out.println("Unit: " + matcher.group("unit"));
            System.out.println("Date: " + matcher.group("time"));
            System.out.println("Seeds: " + matcher.group("seeds"));
        } else {
            System.out.println("No detail matched.");
        }*/
    }
}
