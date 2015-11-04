/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.ui.activities;

import static com.andrew.apollo.Config.MIME_TYPE;
import static com.andrew.apollo.utils.MusicUtils.mService;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;

import com.andrew.apollo.Config;
import com.andrew.apollo.IApolloService;
import com.frostwire.android.R;
import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.loaders.AsyncHandler;
import com.andrew.apollo.loaders.LastAddedLoader;
import com.andrew.apollo.loaders.SearchLoader;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.utils.Lists;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.MusicUtils.ServiceToken;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is opened when the user touches a Home screen shortcut or album
 * art in an app-wdget, and then carries out the proper action. It is also
 * responsible for processing voice queries and playing the spoken artist,
 * album, song, playlist, or genre.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ShortcutActivity extends FragmentActivity implements ServiceConnection {

    /**
     * If true, this class will begin playback and open
     * {@link AudioPlayerActivity}, false will close the class after playback,
     * which is what happens when a user starts playing something from an
     * app-widget
     */
    public static final String OPEN_AUDIO_PLAYER = null;

    /**
     * Service token
     */
    private ServiceToken mToken;

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
    private final ArrayList<Song> mSong = Lists.newArrayList();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);

        // Intiialize the intent
        mIntent = getIntent();
        // Get the voice search query
        mVoiceQuery = Capitalize.capitalize(mIntent.getStringExtra(SearchManager.QUERY));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mService = IApolloService.Stub.asInterface(service);

        // Check for a voice query
        if (mIntent.getAction().equals(Config.PLAY_FROM_SEARCH)) {
            getSupportLoaderManager().initLoader(0, null, mSongAlbumArtistQuery);
        } else if (mService != null) {
            AsyncHandler.post(new Runnable() {
                @Override
                public void run() {
                    final String requestedMimeType = mIntent.getExtras().getString(MIME_TYPE);

                    // First, check the artist MIME type
                    if (MediaStore.Audio.Artists.CONTENT_TYPE.equals(requestedMimeType)) {

                        // Shuffle the artist track list
                        mShouldShuffle = true;

                        // Get the artist song list
                        mList = MusicUtils.getSongListForArtist(ShortcutActivity.this, getId());
                    } else
                    // Second, check the album MIME type
                    if (MediaStore.Audio.Albums.CONTENT_TYPE.equals(requestedMimeType)) {

                        // Shuffle the album track list
                        mShouldShuffle = true;

                        // Get the album song list
                        mList = MusicUtils.getSongListForAlbum(ShortcutActivity.this, getId());
                    } else
                    // Third, check the genre MIME type
                    if (MediaStore.Audio.Genres.CONTENT_TYPE.equals(requestedMimeType)) {

                        // Shuffle the genre track list
                        mShouldShuffle = true;

                        // Get the genre song list
                        mList = MusicUtils.getSongListForGenre(ShortcutActivity.this, getId());
                    } else
                    // Fourth, check the playlist MIME type
                    if (MediaStore.Audio.Playlists.CONTENT_TYPE.equals(requestedMimeType)) {

                        // Don't shuffle the playlist track list
                        mShouldShuffle = false;

                        // Get the playlist song list
                        mList = MusicUtils.getSongListForPlaylist(ShortcutActivity.this, getId());
                    } else
                    // Check the Favorites playlist
                    if (getString(R.string.playlist_favorites).equals(requestedMimeType)) {

                        // Don't shuffle the Favorites track list
                        mShouldShuffle = false;

                        // Get the Favorites song list
                        mList = MusicUtils.getSongListForFavorites(ShortcutActivity.this);
                    } else
                    // Check for the Last added playlist
                    if (getString(R.string.playlist_last_added).equals(requestedMimeType)) {

                        // Don't shuffle the last added track list
                        mShouldShuffle = false;

                        // Get the Last added song list
                        Cursor cursor = LastAddedLoader.makeLastAddedCursor(ShortcutActivity.this);
                        if (cursor != null) {
                            mList = MusicUtils.getSongListForCursor(cursor);
                            cursor.close();
                        }
                    }
                    // Finish up
                    allDone();
                }
            });
        } else {
            // TODO Show and error explaining why
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServiceDisconnected(final ComponentName name) {
        mService = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }
    }

    /**
     * Uses the query from a voice search to try and play a song, then album,
     * then artist. If all of those fail, it checks for playlists and genres via
     * a {@link #mPlaylistGenreQuery}.
     */
    private final LoaderCallbacks<List<Song>> mSongAlbumArtistQuery = new LoaderCallbacks<List<Song>>() {

        /**
         * {@inheritDoc}
         */
        @Override
        public Loader<List<Song>> onCreateLoader(final int id, final Bundle args) {
            return new SearchLoader(ShortcutActivity.this, mVoiceQuery);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onLoadFinished(final Loader<List<Song>> loader, final List<Song> data) {
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
            // Add the data to the adpater
            for (final Song song : data) {
                mSong.add(song);
            }

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
                mList = new long[] {
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

        /**
         * {@inheritDoc}
         */
        @Override
        public void onLoaderReset(final Loader<List<Song>> loader) {
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
        if (mVoiceQuery.equals(favorite)) {
            return true;
        }

        return false;
    }

    /**
     * Starts playback, open {@link AudioPlayerActivity} and finishes this one
     */
    private void allDone() {
        final boolean shouldOpenAudioPlayer = mIntent.getBooleanExtra(OPEN_AUDIO_PLAYER, true);
        // Play the list
        if (mList != null && mList.length > 0) {
            MusicUtils.playAll(this, mList, 0, mShouldShuffle);
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
