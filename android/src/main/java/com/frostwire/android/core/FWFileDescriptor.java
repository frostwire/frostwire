/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.core;

import android.os.Bundle;

import com.frostwire.android.gui.util.UIUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public class FWFileDescriptor implements Cloneable {

    public int id;
    public byte fileType; // As described in Constants.
    public String filePath;
    public long fileSize;
    public String mime; //MIME Type
    public long dateAdded;
    public long dateModified;
    public boolean deletable; // as of Android 11+ we won't be able to target

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
    public FWFileDescriptor() {
    }

    public FWFileDescriptor(int id,
                            String artist,
                            String title,
                            String album,
                            String year,
                            String path,
                            byte fileType,
                            String mime,
                            long fileSize,
                            long dateAdded,
                            long dateModified,
                            boolean deleteable) {
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
        this.deletable = deleteable;
        ensureCorrectMimeType(this);
    }

    public FWFileDescriptor(Bundle bundle) {
        fromBundle(bundle);
        ensureCorrectMimeType(this);
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("id", id);
        bundle.putString("artist", artist);
        bundle.putString("title", title);
        bundle.putString("album", album);
        bundle.putString("year", year);
        bundle.putString("filePath", filePath);
        bundle.putByte("fileType", fileType);
        bundle.putString("mime", mime);
        bundle.putLong("fileSize", fileSize);
        bundle.putLong("dateAdded", dateAdded);
        bundle.putLong("dateModified", dateModified);
        bundle.putBoolean("deletable", deletable);
        return bundle;
    }

    public void fromBundle(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        id = bundle.getInt("id");
        artist = bundle.getString("artist");
        title = bundle.getString("title");
        album = bundle.getString("album");
        year = bundle.getString("year");
        filePath = bundle.getString("filePath");
        fileType = bundle.getByte("fileType");
        mime = bundle.getString("mime");
        fileSize = bundle.getLong("fileSize");
        dateAdded = bundle.getLong("dateAdded");
        dateModified = bundle.getLong("dateModified");
        deletable = bundle.getBoolean("deletable");
    }

    @Override
    public String toString() {
        return "FD(id:" + id + ", ft:" + fileType + ", t:" + title + ", p:" + filePath + ", d:" + deletable + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FWFileDescriptor)) {
            return false;
        }
        FWFileDescriptor fd = (FWFileDescriptor) o;
        return (this.id == fd.id && this.fileType == fd.fileType) || (this.filePath != null && fd.filePath != null && this.filePath.equals(fd.filePath));
    }

    @Override
    public int hashCode() {
        return this.id * 1000 + this.fileType;
    }

    @Override
    public FWFileDescriptor clone() {
        return new FWFileDescriptor(id, artist, title, album, year, filePath, fileType, mime, fileSize, dateAdded, dateModified, deletable);
    }

    private void ensureCorrectMimeType(FWFileDescriptor fd) {
        if (fd.mime == null || fd.filePath.endsWith(".torrent") || fd.filePath.endsWith(".apk")) {
            fd.mime = UIUtils.getMimeType(fd.filePath);
        }
    }
}
