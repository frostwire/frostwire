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

package com.frostwire.android.gui.transfers;

import com.frostwire.frostclick.Slide;
import com.frostwire.search.AbstractFileSearchResult;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class HttpSlideSearchResult extends AbstractFileSearchResult {

    private final Slide slide;

    public HttpSlideSearchResult(Slide slide) {
        this.slide = slide;
    }

    @Override
    public String getDisplayName() {
        return slide.title;
    }

    @Override
    public long getSize() {
        return slide.size;
    }

    public String getHttpUrl() {
        return slide.httpDownloadURL;
    }

    public boolean isCompressed() {
        return slide.uncompress;
    }

    @Override
    public String getFilename() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getSource() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getDetailsUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    public Slide slide() {
        return slide;
    }

    @Override
    public long getCreationTime() {
        // TODO Auto-generated method stub
        return 0;
    }
}
