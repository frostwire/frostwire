package com.frostwire.alexandria;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.frostwire.alexandria.db.LibraryDB;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.alexandria.db.LibraryDatabaseEntity;
import com.frostwire.alexandria.db.PlaylistDB;

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
        // perform name sort here. It is no the best place
        List<Playlist> list = PlaylistDB.getPlaylists(db);
        Collections.sort(list, new Comparator<Playlist>() {
            @Override
            public int compare(Playlist o1, Playlist o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return list;
    }

    public Playlist getPlaylist(String name) {
        return PlaylistDB.getPlaylist(db, name);
    }

    public Playlist newPlaylist(String name, String description) {
        return new Playlist(db, LibraryDatabase.OBJECT_NOT_SAVED_ID, name, description);
    }

    public void dump() {
        db.dump();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public Playlist getStarredPlaylist() {
        return PlaylistDB.getStarredPlaylist(db);
    }

    public void updatePlaylistItemProperties(String filePath, String title, String artist, String album, String comment, String genre, String track, String year) {
        PlaylistDB.updatePlaylistItemProperties(db, filePath, title, artist, album, comment, genre, track, year);
    }
}
