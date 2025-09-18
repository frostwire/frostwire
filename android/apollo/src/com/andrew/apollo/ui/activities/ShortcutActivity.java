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

package com.andrew.apollo.ui.activities;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import com.andrew.apollo.Config;
import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.loaders.SearchLoader;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is opened when the user touches a Home screen shortcut or album
 * art in an app-widget, and then carries out the proper action. It is also
 * responsible for processing voice queries and playing the spoken artist,
 * album, song, playlist, or genre.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class ShortcutActivity extends AppCompatActivity {

    /**
     * If true, this class will begin playback and open
     * {@link AudioPlayerActivity}, false will close the class after playback,
     * which is what happens when a user starts playing something from an
     * app-widget
     */
    private static final String OPEN_AUDIO_PLAYER = null;

    /**
     * Gather the intent action and extras
     */
    private Intent mIntent;

    /**
     * The list of songs to play
     */
    private long[] mList;

    /**
     * Used to shuffle the tracks or play them in order
     */
    private boolean mShouldShuffle;

    /**
     * Search query from a voice action
     */
    private String mVoiceQuery;

    /**
     * Used with the loader and voice queries
     */
    private final ArrayList<Song> mSong = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Bind Apollo's service
        //mToken = MusicUtils.bindToService(this, this);

        // Initialize the intent
        mIntent = getIntent();
        // Get the voice search query
        mVoiceQuery = Capitalize.capitalize(mIntent.getStringExtra(SearchManager.QUERY));

    }

    /**
     * Uses the query from a voice search to try and play a song, then album,
     * then artist. If all of those fail, it checks for playlists and genres
     */
    private final LoaderCallbacks<List<Song>> mSongAlbumArtistQuery = new LoaderCallbacks<>() {

        @Override
        public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
            return new SearchLoader(ShortcutActivity.this, mVoiceQuery);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<List<Song>> loader, List<Song> data) {
            // If the user searched for a playlist or genre, this list will
            // return empty
            if (data.isEmpty()) {
                // Before running the playlist loader, try to play the
                // "Favorites" playlist
                if (isFavorite()) {
                    MusicUtils.playFavorites(ShortcutActivity.this);
                }
                // Finish up
                allDone();
                return;
            }

            // Start fresh
            mSong.clear();
            // Add the data to the adapter
            mSong.addAll(data);

            // What's about to happen is similar to the above process. Apollo
            // runs a
            // series of checks to see if anything comes up. When it does, it
            // assumes (pretty accurately) that it should begin to play that
            // thing.
            // The fancy search query used in {@link SearchLoader} is the key to
            // this. It allows the user to perform very specific queries. i.e.
            // "Listen to Ethio

            final String song = mSong.get(0).mSongName;
            final String album = mSong.get(0).mAlbumName;
            final String artist = mSong.get(0).mArtistName;
            // This tripes as the song, album, and artist Id
            final long id = mSong.get(0).mSongId;
            // First, try to play a song
            if (mList == null && song != null) {
                mList = new long[]{
                        id
                };
            } else
                // Second, try to play an album
                if (mList == null && album != null) {
                    mList = MusicUtils.getSongListForAlbum(ShortcutActivity.this, id);
                } else
                    // Third, try to play an artist
                    if (mList == null && artist != null) {
                        mList = MusicUtils.getSongListForArtist(ShortcutActivity.this, id);
                    }
            // Finish up
            allDone();
        }

        @Override
        public void onLoaderReset(@NonNull androidx.loader.content.Loader<List<Song>> loader) {
            // Clear the data
            mSong.clear();
        }
    };

    /**
     * Used to find the Id supplied
     *
     * @return The Id passed into the activity
     */
    private long getId() {
        return mIntent.getExtras().getLong(Config.ID);
    }

    /**
     * @return True if the user searched for the favorites playlist
     */
    private boolean isFavorite() {
        // Check to see if the user spoke the word "Favorites"
        final String favoritePlaylist = getString(R.string.playlist_favorites);
        if (mVoiceQuery.equals(favoritePlaylist)) {
            return true;
        }

        // Check to see if the user spoke the word "Favorite"
        final String favorite = getString(R.string.playlist_favorite);
        return mVoiceQuery.equals(favorite);
    }

    /**
     * Starts playback, open {@link AudioPlayerActivity} and finishes this one
     */
    private void allDone() {
        final boolean shouldOpenAudioPlayer = mIntent.getBooleanExtra(OPEN_AUDIO_PLAYER, true);
        // Play the list
        if (mList != null && mList.length > 0) {
            MusicUtils.playFDs(mList, 0, mShouldShuffle);
        }

        // Open the now playing screen
        if (shouldOpenAudioPlayer) {
            final Intent intent = new Intent(this, AudioPlayerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        // All done
        finish();
    }
}
