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

package com.andrew.apollo.ui.activities;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import com.andrew.apollo.Config;
import com.andrew.apollo.format.Capitalize;
import com.andrew.apollo.loaders.SearchLoader;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * This class is opened when the user touches a Home screen shortcut or album
 * art in an app-widget, and then carries out the proper action. It is also
 * responsible for processing voice queries and playing the spoken artist,
 * album, song, playlist, or genre.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class ShortcutActivity extends AppCompatActivity {

    private static final Logger LOG = Logger.getLogger(ShortcutActivity.class);

    /**
     * If true, this class will begin playback and open
     * {@link AudioPlayerActivity}, false will close the class after playback,
     * which is what happens when a user starts playing something from an
     * app-widget
     */
    private static final String OPEN_AUDIO_PLAYER = "open_audio_player";

    /** {@link Config#MIME_TYPE} of a playlist shortcut, as ProfileActivity sets it. */
    static final String PLAYLIST_MIME_TYPE = "vnd.android.cursor.dir/playlist";

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


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fade it in
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            @SuppressWarnings("deprecation")
            Runnable fadeTransition = () -> overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            fadeTransition.run();
        }

        // Bind Apollo's service
        //mToken = MusicUtils.bindToService(this, this);

        // Initialize the intent
        mIntent = getIntent();
        // Get the voice search query
        mVoiceQuery = Capitalize.capitalize(mIntent.getStringExtra(SearchManager.QUERY));

        if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(mIntent.getAction()) && mVoiceQuery != null) {
            // The loader resolves the spoken query to songs, then allDone() plays them
            LoaderManager.getInstance(this).initLoader(0, null, mSongAlbumArtistQuery);
        } else if (mIntent.hasExtra(Config.MIME_TYPE)) {
            // A home screen shortcut: its song list is a MediaStore query, kept off the main thread
            final Context appContext = getApplicationContext();
            final String mimeType = mIntent.getStringExtra(Config.MIME_TYPE);
            final long id = mIntent.getLongExtra(Config.ID, -1);
            playOffMain(() -> songListForShortcut(appContext, mimeType, id));
        } else {
            // Nothing to play
            finish();
        }
    }

    /**
     * Looks up the songs on the MISC thread (MediaStore queries; MusicUtils refuses them on the
     * main thread), then plays them and finishes on the main thread.
     */
    private void playOffMain(final Supplier<long[]> lookup) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            long[] songs = null;
            try {
                songs = lookup.get();
            } catch (RuntimeException e) {
                // e.g. no permission to read the music library; finish either way
                LOG.warn("ShortcutActivity: could not look up the songs to play: " + e.getMessage(), e);
            }
            final long[] list = songs;
            final boolean shuffle = MusicUtils.isShuffleEnabled();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                mList = list;
                mShouldShuffle = shuffle;
                allDone();
            });
        });
    }

    /**
     * The songs a voice search's best match stands for: the song itself, else its album, else its
     * artist. Queries MediaStore: never on the main thread.
     */
    static long[] songListForVoiceMatch(final Context context, final Song match) {
        if (match.mSongName != null) {
            return new long[]{match.mSongId};
        }
        // The SearchLoader's id triples as the song, album and artist id
        if (match.mAlbumName != null) {
            return MusicUtils.getSongListForAlbum(context, match.mSongId);
        }
        if (match.mArtistName != null) {
            return MusicUtils.getSongListForArtist(context, match.mSongId);
        }
        return null;
    }

    /**
     * The songs a home screen shortcut stands for, from the {@link Config#MIME_TYPE} and
     * {@link Config#ID} that ProfileActivity put in it, or null for an unknown type.
     * Queries MediaStore: never on the main thread.
     */
    static long[] songListForShortcut(final Context context, final String mimeType, final long id) {
        if (MediaStore.Audio.Artists.CONTENT_TYPE.equals(mimeType)) {
            return MusicUtils.getSongListForArtist(context, id);
        }
        if (MediaStore.Audio.Albums.CONTENT_TYPE.equals(mimeType)) {
            return MusicUtils.getSongListForAlbum(context, id);
        }
        if (MediaStore.Audio.Genres.CONTENT_TYPE.equals(mimeType)) {
            return MusicUtils.getSongListForGenre(context, id);
        }
        if (PLAYLIST_MIME_TYPE.equals(mimeType)) {
            return MusicUtils.getSongListForPlaylist(context, id);
        }
        if (context.getString(R.string.playlist_favorites).equals(mimeType)) {
            return MusicUtils.getSongListForFavorites(context);
        }
        if (context.getString(R.string.playlist_last_added).equals(mimeType)) {
            return MusicUtils.getSongListForLastAdded(context);
        }
        return null;
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
            final Context appContext = getApplicationContext();
            if (data == null || data.isEmpty()) {
                // A playlist or genre search finds no songs; the "Favorites" playlist is the one
                // that can still be played
                final boolean favorite = isFavorite();
                playOffMain(() -> favorite ? MusicUtils.getSongListForFavorites(appContext) : null);
                return;
            }
            final Song match = data.get(0);
            playOffMain(() -> songListForVoiceMatch(appContext, match));
        }

        @Override
        public void onLoaderReset(@NonNull androidx.loader.content.Loader<List<Song>> loader) {
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
        if (mList != null && mList.length > 0) {
            // A shortcut can start the app, so the playback service may not be running yet
            final long[] list = mList;
            final boolean shuffle = mShouldShuffle;
            MusicUtils.executeWithMusicPlaybackService(getApplicationContext(),
                    () -> MusicUtils.playFDs(list, 0, shuffle));

            // Open the now playing screen
            if (shouldOpenAudioPlayer) {
                final Intent intent = new Intent(this, AudioPlayerActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        }
        // All done
        finish();
    }
}
