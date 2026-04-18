/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.internetarchive;

import com.frostwire.search.AbstractCrawledSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.util.UrlUtils;
import org.apache.commons.io.FilenameUtils;

import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public class InternetArchiveCrawledSearchResult extends AbstractCrawledSearchResult<InternetArchiveSearchResult> implements HttpSearchResult {
    private static final String DOWNLOAD_URL = "https://%s/download/%s/%s";
    private final String filename;
    private final String displayName;
    private final String downloadUrl;
    private final long size;

    public InternetArchiveCrawledSearchResult(InternetArchiveSearchResult sr, InternetArchiveFile file) {
        super(sr);
        this.filename = FilenameUtils.getName(file.filename);
        this.displayName = FilenameUtils.getBaseName(filename) + " (" + sr.getDisplayName() + ")";
        this.downloadUrl = buildDownloadUrl(sr.getDomainName(), sr.getIdentifier(), file.filename);
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

    private long calcSize(InternetArchiveFile file) {
        try {
            return Long.parseLong(file.size);
        } catch (Throwable e) {
            return -1;
        }
    }

    static String buildDownloadUrl(String domainName, String identifier, String filename) {
        return String.format(Locale.US, DOWNLOAD_URL, domainName, identifier, encodeArchivePath(filename));
    }

    private static String encodeArchivePath(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        String[] segments = filename.split("/", -1);
        StringBuilder encoded = new StringBuilder(filename.length());
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(UrlUtils.encode(segments[i]));
        }
        return encoded.toString();
    }
}
