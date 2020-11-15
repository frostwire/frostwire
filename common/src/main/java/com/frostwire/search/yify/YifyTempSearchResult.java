/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search.yify;

import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

/**
 * @author gubatron
 * @author aldenml
 */
final class YifyTempSearchResult extends AbstractSearchResult implements CrawlableSearchResult {
    private final String detailsUrl;

    YifyTempSearchResult(String domainName, String itemId, String htmlFilename) {
        this.detailsUrl = "https://" + domainName + "/movie/" + itemId + "/" + htmlFilename;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return false;
    }
}
