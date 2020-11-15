/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.

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
