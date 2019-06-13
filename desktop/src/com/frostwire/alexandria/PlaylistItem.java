/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.alexandria;

import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.alexandria.db.LibraryDatabaseEntity;
import com.frostwire.alexandria.db.PlaylistItemDB;

import static com.frostwire.util.StringUtils.isNullOrEmpty;

public class PlaylistItem extends LibraryDatabaseEntity {
    private Playlist playlist;
    private int id;
    private String filePath;
    private String fileName;
    private long fileSize;
    private String fileExtension;
    private String trackTitle;
    private float trackDurationInSecs;
    private String trackArtist;
    private String trackAlbum;
    private String coverArtPath;
    private String trackBitrate;
    private String trackComment;
    private String trackGenre;
    private String trackNumber;
    private String trackYear;
    private boolean starred;
    private int sortIndex;

    public PlaylistItem(Playlist playlist) {
        super(playlist != null ? playlist.getLibraryDatabase() : null);
        this.playlist = playlist;
        this.id = LibraryDatabase.OBJECT_INVALID_ID;
    }

    public PlaylistItem(Playlist playlist, int id, String filePath, String fileName, long fileSize, String fileExtension, String trackTitle, float trackDurationInSecs, String trackArtist, String trackAlbum, String coverArtPath, String trackBitrate, String trackComment, String trackGenre,
                        String trackNumber, String trackYear, boolean starred) {
        super(playlist != null ? playlist.getLibraryDatabase() : null);
        this.playlist = playlist;
        this.id = id;
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileExtension = fileExtension;
        this.trackTitle = trackTitle;
        this.trackDurationInSecs = trackDurationInSecs;
        this.trackArtist = trackArtist;
        this.trackAlbum = trackAlbum;
        this.coverArtPath = coverArtPath;
        this.trackBitrate = trackBitrate;
        this.trackComment = trackComment;
        this.trackGenre = trackGenre;
        this.trackNumber = trackNumber;
        this.trackYear = trackYear;
        this.starred = starred;
        this.sortIndex = playlist != null ? (playlist.getItems().size() + 1) : 0; // set sortIndex to the last position (1-based) by default
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
        setLibraryDatabase(playlist.getLibraryDatabase());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public void setTrackTitle(String trackTitle) {
        this.trackTitle = trackTitle;
    }

    public float getTrackDurationInSecs() {
        return trackDurationInSecs;
    }

    public void setTrackDurationInSecs(float trackDurationInSecs) {
        this.trackDurationInSecs = trackDurationInSecs;
    }

    public String getTrackArtist() {
        return trackArtist;
    }

    public void setTrackArtist(String artistName) {
        this.trackArtist = artistName;
    }

    public String getTrackAlbum() {
        return trackAlbum;
    }

    public void setTrackAlbum(String albumName) {
        this.trackAlbum = albumName;
    }

    public String getCoverArtPath() {
        return coverArtPath;
    }

    public void setCoverArtPath(String coverArtPath) {
        this.coverArtPath = coverArtPath;
    }

    public String getTrackBitrate() {
        return trackBitrate;
    }

    public void setTrackBitrate(String bitrate) {
        this.trackBitrate = bitrate;
    }

    public String getTrackComment() {
        return trackComment;
    }

    public void setTrackComment(String comment) {
        this.trackComment = comment;
    }

    public String getTrackGenre() {
        return trackGenre;
    }

    public void setTrackGenre(String genre) {
        this.trackGenre = genre;
    }

    public String getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(String trackNum) {
        if ("null".equals(trackNum)) {
            trackNum = "-1";
        }
        this.trackNumber = trackNum;
    }

    public String getTrackYear() {
        return trackYear;
    }

    public void setTrackYear(String year) {
        this.trackYear = year;
    }

    public boolean isStarred() {
        return starred;
    }

    public void setStarred(boolean starred) {
        this.starred = starred;
    }

    public void save() {
        save(false);
    }

    public void save(boolean updateStarred) {
        if (db != null) {
            PlaylistItemDB.save(db, this, updateStarred);
        }
    }

    public void delete() {
        if (db != null) {
            PlaylistItemDB.delete(db, this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PlaylistItem && this.id == ((PlaylistItem) obj).id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "(" + id + ", title:" + trackTitle + ", number:" + trackNumber + ")";
    }

    public int getSortIndex() {
        return sortIndex;
    }

    private void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    /**
     * Attempts to look for the track number to set this number as the sorting index.
     * In case it cannot find a track number it will use the fallback value.
     * <p>
     * Indexes start at 1. (not 0)
     *
     * @param fallBackIndexValue the sorting index to use if a track number isn't found.
     */
    public void setSortIndexByTrackNumber(int fallBackIndexValue) {
        int sortIndex = fallBackIndexValue;
        try {
            if (!isNullOrEmpty(getTrackNumber())) {
                sortIndex = Integer.parseInt(getTrackNumber().trim());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        setSortIndex(sortIndex);
    }
}