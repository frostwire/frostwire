/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 
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

package com.frostwire.android.tests.search;

import junit.framework.TestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.frostwire.search.WebSearchPerformer;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class WebSearchPerformerTest extends TestCase {

    @SmallTest
    public void testKeywordsEncoding() {
        testEncoding("A B%C", "A%20B%25C");
        testEncoding("A+B_Ó", "A%2BB_%C3%93");
        testEncoding("&&^^{}", "%26%26%5E%5E%7B%7D");
        testEncoding("P~~Lüñ", "P%7E%7EL%C3%BC%C3%B1");
        testEncoding("#@++--==\\", "%23%40%2B%2B--%3D%3D%5C");
        testEncoding("T:;\"", "T%3A%3B%22");
    }

    private void testEncoding(String uk, String ek) {
        assertEquals("Wrong encoding for: " + uk, ek, encodeKeywords(uk));
    }

    private String encodeKeywords(String keywords) {
        final StringBuilder sb = new StringBuilder();

        WebSearchPerformer p = new WebSearchPerformer(null, 0, keywords, 0) {
            @Override
            public void perform() {
                sb.append(getEncodedKeywords());
            }
        };

        p.perform();

        return sb.toString();
    }
}
