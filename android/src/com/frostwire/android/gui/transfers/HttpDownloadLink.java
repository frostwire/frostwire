/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.transfers;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class HttpDownloadLink {

    private final String url;
    private final String filename;
    private final String displayName;
    private final long size;
    private final boolean compressed;

    public HttpDownloadLink(String url, String filename, String displayName, long size, boolean compressed) {
        this.url = url;
        this.filename = filename;
        this.displayName = displayName;
        this.size = size;
        this.compressed = compressed;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return filename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getSize() {
        return size;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public HttpDownloadLink withFilename(String filename) {
        return new HttpDownloadLink(this.url, filename, this.displayName, this.size, this.compressed);
    }
    
    protected static String getValidFileName(String fileName) {
        String newFileName = fileName.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_");
        return newFileName;
    }
}
