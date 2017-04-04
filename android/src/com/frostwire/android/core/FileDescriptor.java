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

package com.frostwire.android.core;

import com.frostwire.android.gui.util.UIUtils;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public class FileDescriptor implements Cloneable {

    public int id;
    public byte fileType; // As described in Constants.
    public String filePath;
    public long fileSize;
    public String mime; //MIME Type
    public boolean shared;
    public long dateAdded;
    public long dateModified;

    /**
     * The title of the content.
     */
    public String title;

    // only if audio/video media

    /**
     * The artist who created the media file, if any.
     */
    public String artist;

    /**
     * The album the media file is from, if any.
     */
    public String album;

    /**
     * The year the media file was recorded, if any
     */
    public String year;
    
    public long albumId;

    /**
     * Empty constructor. Needed for in the JSON (and Gson) serialization process.
     */
    public FileDescriptor() {
    }

    public FileDescriptor(int id, String artist, String title, String album, String year, String path, byte fileType, String mime, long fileSize, long dateAdded, long dateModified, boolean isShared) {
        this.id = id;
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.year = year;
        this.filePath = path;
        this.fileType = fileType;
        this.mime = mime;
        this.fileSize = fileSize;
        this.dateAdded = dateAdded;
        this.dateModified = dateModified;
        this.shared = isShared;
        ensureCorrectMimeType(this);
    }

    @Override
    public String toString() {
        return "FD(id:" + id + ", ft:" + fileType + ", t:" + title + ", p:" + filePath + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof FileDescriptor)) {
            return false;
        }

        FileDescriptor fd = (FileDescriptor) o;

        if (this.id == fd.id && this.fileType == fd.fileType) {
            return true;
        }

        if (this.filePath != null && fd.filePath != null && this.filePath.equals(fd.filePath)) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return this.id * 1000 + this.fileType;
    }

    @Override
    public FileDescriptor clone() {
        return new FileDescriptor(id, artist, title, album, year, filePath, fileType, mime, fileSize, dateAdded, dateModified, shared);
    }

    private void ensureCorrectMimeType(FileDescriptor fd) {
        if (fd.mime == null || fd.filePath.endsWith(".torrent") || fd.filePath.endsWith(".apk")) {
            fd.mime = UIUtils.getMimeType(fd.filePath);
        }
    }
}
