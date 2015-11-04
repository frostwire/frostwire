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

package com.frostwire.android.core;

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
}
