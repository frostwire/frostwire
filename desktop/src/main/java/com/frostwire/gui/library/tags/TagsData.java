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

package com.frostwire.gui.library.tags;

/**
 * @author aldenml
 * @author gubatron
 */
public class TagsData {
    private final int duration;
    private final String bitrate;
    private final String title;
    private final String artist;
    private final String album;
    private final String comment;
    private final String genre;
    private final String track;
    private final String year;
    private final String lyrics;

    TagsData(int duration, String bitrate, String title, String artist, String album, String comment, String genre, String track, String year, String lyrics) {
        this.duration = duration;
        this.bitrate = bitrate;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.comment = comment;
        this.genre = genre;
        this.track = track;
        this.year = year;
        this.lyrics = lyrics;
    }

    public int getDuration() {
        return duration;
    }

    public String getBitrate() {
        return bitrate;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getComment() {
        return comment;
    }

    public String getGenre() {
        return genre;
    }

    public String getTrack() {
        return track;
    }

    public String getYear() {
        return year;
    }

    public String getLyrics() {
        return lyrics;
    }
}
