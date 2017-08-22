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

package com.frostwire.search.pixabay;

import com.frostwire.search.HttpSearchResult;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PixabayImageSearchResult extends PixabayItemSearchResult implements HttpSearchResult {

    private final String displayName;
    private final String filename;

    PixabayImageSearchResult(PixabayItem item) {
        super(item);
        this.displayName = FilenameUtils.getName(item.previewURL);
        this.filename = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getThumbnailUrl() {
        return item.previewURL;
    }

    @Override
    public String getDownloadUrl() {
        return item.webformatURL;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return -1;
    }
}
