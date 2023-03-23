/*
 * Created by Angel Leon (@gubatron)
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

package com.frostwire.search.telluride;

import com.frostwire.licenses.License;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

public class TellurideSearchResult implements HttpSearchResult {
    private final String id;
    private final String title;
    private final String filename;
    private final String source;
    private final String detailsUrl;
    private final String downloadUrl;
    private final String thumbnail;
    private final long fileSize;
    private final long creationTime;

    public TellurideSearchResult(
            String _id,
            String _title,
            String _filename,
            String _source,
            String _detailsUrl,
            String _downloadUrl,
            String _thumbnail,
            long _fileSize,
            long _creationTime) {
        id = _id;
        title = StringUtils.removeDoubleSpaces(StringUtils.removeUnicodeCharacters(_title));
        filename = FilenameUtils.sanitizeFilename(_filename);
        source = _source;
        detailsUrl = _detailsUrl;
        downloadUrl = _downloadUrl;
        fileSize = _fileSize;
        thumbnail = _thumbnail;
        creationTime = _creationTime;
    }

    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return title;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public License getLicense() {
        return null;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnail;
    }

    @Override
    public String getDownloadUrl() {
        return downloadUrl;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return fileSize;
    }
}
