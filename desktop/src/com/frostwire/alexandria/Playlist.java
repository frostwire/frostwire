/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, 2013, FrostWire(R). All rights reserved.

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

package com.frostwire.alexandria;

import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.alexandria.db.LibraryDatabaseEntity;
import com.frostwire.alexandria.db.PlaylistDB;
import com.frostwire.alexandria.db.PlaylistItemDB;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class Playlist extends LibraryDatabaseEntity {
    private int _id;
    private String _name;
    private String _description;
    private boolean deleted;
    private final List<PlaylistItem> _items;

    public Playlist(LibraryDatabase libraryDB) {
        super(libraryDB);
        _id = LibraryDatabase.OBJECT_INVALID_ID;
        _items = Collections.synchronizedList(new LinkedList<>());
        this.deleted = false;
    }

    public Playlist(LibraryDatabase libraryDB, int id, String name, String description) {
        super(libraryDB);
        _id = id;
        _name = name;
        _description = description;
        _items = new LinkedList<>();
        this.deleted = false;
    }

    public final boolean isStarred() {
        return _id == LibraryDatabase.STARRED_PLAYLIST_ID;
    }

    public int getId() {
        return _id;
    }

    public void setId(int id) {
        _id = id;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    public List<PlaylistItem> getItems() {
        return _items;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public synchronized void save() {
        if (db != null) {
            PlaylistDB.save(db, this);
        }
    }

    public synchronized void delete() {
        if (db != null) {
            PlaylistDB.delete(db, this);
            deleted = true;
        }
    }

    public synchronized void refresh() {
        if (db != null) {
            _items.clear();
            _items.addAll(PlaylistItemDB.getPlaylistItems(db, this));
        }
    }

    public PlaylistItem newItem(String filePath, String fileName, long fileSize, String fileExtension, String trackTitle, float trackDurationInSecs, String trackArtist, String trackAlbum, String coverArtPath, String trackBitrate, String trackComment, String trackGenre, String trackNumber,
                                String trackYear, boolean starred) {
        return new PlaylistItem(this, LibraryDatabase.OBJECT_NOT_SAVED_ID, filePath, fileName, fileSize, fileExtension, trackTitle, trackDurationInSecs, trackArtist, trackAlbum, coverArtPath, trackBitrate, trackComment, trackGenre, trackNumber, trackYear, starred);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Playlist)) {
            return false;
        }
        Playlist other = (Playlist) obj;
        return other.getId() == getId();
    }

    @Override
    public String toString() {
        return _name;
    }
}
