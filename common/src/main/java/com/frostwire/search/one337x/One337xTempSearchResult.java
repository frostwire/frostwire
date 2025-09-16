/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search.one337x;

import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

/**
 * @author gubatron
 * @author aldenml
 * @author HimanshuSharma789
 */
final class One337xTempSearchResult extends AbstractSearchResult implements CrawlableSearchResult {
    private final String detailsUrl;
    private final String displayName;

    One337xTempSearchResult(String domainName, String itemId, String htmlFilename, String displayName) {
        this.detailsUrl = "https://" + domainName + "/torrent/" + itemId + "/" + htmlFilename;
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return "1337x";
    }

    @Override
    public boolean isComplete() {
        return false;
    }
}
