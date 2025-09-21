/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
