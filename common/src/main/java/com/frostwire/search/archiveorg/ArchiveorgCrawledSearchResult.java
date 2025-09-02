/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.frostwire.search.archiveorg;

import com.frostwire.search.AbstractCrawledSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.util.UrlUtils;
import org.apache.commons.io.FilenameUtils;

import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public class ArchiveorgCrawledSearchResult extends AbstractCrawledSearchResult<ArchiveorgSearchResult> implements HttpSearchResult {
    private static final String DOWNLOAD_URL = "https://%s/download/%s/%s";
    private final String filename;
    private final String displayName;
    private final String downloadUrl;
    private final long size;

    public ArchiveorgCrawledSearchResult(ArchiveorgSearchResult sr, ArchiveorgFile file) {
        super(sr);
        this.filename = FilenameUtils.getName(file.filename);
        this.displayName = FilenameUtils.getBaseName(filename) + " (" + sr.getDisplayName() + ")";
        this.downloadUrl = String.format(Locale.US, DOWNLOAD_URL, sr.getDomainName(), sr.getIdentifier(), UrlUtils.encode(file.filename));
        this.size = calcSize(file);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getDownloadUrl() {
        return downloadUrl;
    }

    private long calcSize(ArchiveorgFile file) {
        try {
            return Long.parseLong(file.size);
        } catch (Throwable e) {
            return -1;
        }
    }
}
