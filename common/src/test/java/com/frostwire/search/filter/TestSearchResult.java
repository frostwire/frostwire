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

package com.frostwire.search.filter;

import com.frostwire.licenses.License;
import com.frostwire.search.SearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
class TestSearchResult implements SearchResult, Cloneable {

    private String id;

    private String source;

    public TestSearchResult(String id) {
        this.id = id;

        this.source = "source:" + id;
    }

    @Override
    public String getDisplayName() {
        return "DisplayName:" + id;
    }

    @Override
    public String getDetailsUrl() {
        return "http://" + id + ".com/details";
    }

    @Override
    public long getCreationTime() {
        return 0;
    }

    @Override
    public String getSource() {
        return source;
    }

    public TestSearchResult source(String source) {
        this.source = source;
        return this;
    }

    @Override
    public License getLicense() {
        return null;
    }

    @Override
    public String getThumbnailUrl() {
        return "http://" + id + ".com/thumbnail";
    }

    @Override
    public int uid() {
        return 0;
    }

    @Override
    public TestSearchResult clone() {
        TestSearchResult sr = new TestSearchResult(id);

        sr.source = source;

        return sr;
    }
}
