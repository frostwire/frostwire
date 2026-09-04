/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, String> httpHeaders;
    /**
     * Hidden DASH mux sibling: best MP4-container audio (m4a/AAC) for this
     * video row, fetched automatically at download time and muxed into the
     * video so DASH video-only results play with sound. Null when the video
     * is already muxed or no MP4 audio exists. Never shown as its own row
     * (the visible audio row keeps the best audio regardless of container).
     */
    private String muxAudioUrl;
    private String muxAudioExt;
    private long muxAudioFilesize;
    private Map<String, String> muxAudioHeaders;

    public TellurideSearchResult(
            String _id,
            String _title,
            String _filename,
            String _source,
            String _detailsUrl,
            String _downloadUrl,
            String _thumbnail,
            long _fileSize,
            long _creationTime,
            Map<String, String> _httpHeaders) {
        id = _id;
        title = StringUtils.removeDoubleSpaces(StringUtils.removeUnicodeCharacters(_title));
        filename = FilenameUtils.sanitizeFilename(_filename);
        source = _source;
        detailsUrl = _detailsUrl;
        downloadUrl = _downloadUrl;
        fileSize = _fileSize;
        thumbnail = _thumbnail;
        creationTime = _creationTime;
        httpHeaders = copyHttpHeaders(_httpHeaders);
    }

    public TellurideSearchResult(
            String _id,
            String _title,
            String _source,
            String _detailsUrl,
            String _thumbnail,
            long _creationTime) {
        id = _id;
        title = StringUtils.removeDoubleSpaces(StringUtils.removeUnicodeCharacters(_title));
        filename = _title;
        source = _source;
        detailsUrl = _detailsUrl;
        downloadUrl = null;
        fileSize = 0;
        thumbnail = _thumbnail;
        creationTime = _creationTime;
        httpHeaders = null;
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

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    /** Hidden mux sibling setter, called once by the performer for DASH video rows. */
    public void setMuxAudio(String url, String ext, long filesize, Map<String, String> headers) {
        muxAudioUrl = url;
        muxAudioExt = ext;
        muxAudioFilesize = filesize;
        muxAudioHeaders = copyHttpHeaders(headers);
    }

    /** Null unless this video row carries a hidden MP4-container audio sibling. */
    public String getMuxAudioUrl() {
        return muxAudioUrl;
    }

    public String getMuxAudioExt() {
        return muxAudioExt;
    }

    public long getMuxAudioFilesize() {
        return muxAudioFilesize;
    }

    public Map<String, String> getMuxAudioHeaders() {
        return muxAudioHeaders;
    }

    /** True when downloading this result should also fetch + mux the sibling audio. */
    public boolean needsAudioMux() {
        return muxAudioUrl != null && !muxAudioUrl.isEmpty();
    }

    private static Map<String, String> copyHttpHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        return Collections.unmodifiableMap(new HashMap<>(headers));
    }
}
