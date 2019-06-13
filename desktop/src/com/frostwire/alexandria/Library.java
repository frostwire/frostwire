/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.alexandria;

import com.frostwire.alexandria.db.LibraryDB;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.alexandria.db.LibraryDatabaseEntity;
import com.frostwire.alexandria.db.PlaylistDB;

import java.io.File;
import java.util.Comparator;
import java.util.List;

public class Library extends LibraryDatabaseEntity {
    private int _id;
    private String _name;
    private int _version;

    public Library(File libraryFile) {
        super(new LibraryDatabase(libraryFile));
        LibraryDB.fill(db, this);
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

    public int getVersion() {
        return _version;
    }

    public void setVersion(int version) {
        _version = version;
    }

    public void close() {
        db.close();
    }

    public List<Playlist> getPlaylists() {
        List<Playlist> list = PlaylistDB.getPlaylists(db);
        list.sort(Comparator.comparing(Playlist::getName));
        return list;
    }

    public Playlist newPlaylist(String name, String description) {
        return new Playlist(db, LibraryDatabase.OBJECT_NOT_SAVED_ID, name, description);
    }

    public Playlist getStarredPlaylist() {
        return PlaylistDB.getStarredPlaylist(db);
    }

    public void updatePlaylistItemProperties(String filePath, String title, String artist, String album, String comment, String genre, String track, String year) {
        PlaylistDB.updatePlaylistItemProperties(db, filePath, title, artist, album, comment, genre, track, year);
    }
}
