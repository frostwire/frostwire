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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import androidx.appcompat.widget.Toolbar;

import com.andrew.apollo.Config;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.MusicStateListener;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.PlayPauseButton;
import com.andrew.apollo.widgets.RepeatButton;
import com.andrew.apollo.widgets.RepeatingImageButton;
import com.andrew.apollo.widgets.ShuffleButton;
import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.menu.CreateNewPlaylistMenuAction;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.offers.HeaderBanner;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Objects;

/**
 * A base {@link Activity} used to update the bottom bar and
 * bind to Apollo's service.
 * <p>
 * {@link HomeActivity} extends from this skeleton.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class BaseActivity extends AbstractActivity {
    /**
     * Play state and meta change listener
     */
    private final ArrayList<MusicStateListener> mMusicStateListener = new ArrayList<>();

    /**
     * Play and pause button (BAB)
     */
    private PlayPauseButton mPlayPauseButton;

    /**
     * Repeat button (BAB)
     */
    private RepeatButton mRepeatButton;

    /**
     * Shuffle button (BAB)
     */
    private ShuffleButton mShuffleButton;

    /**
     * Track name (BAB)
     */
    private TextView mTrackName;

    /**
     * Artist name (BAB)
     */
    private TextView mArtistName;

    /**
     * Album art (BAB)
     */
    private ImageView mAlbumArt;

    /**
     * Broadcast receiver
     */
    private PlaybackStatus mPlaybackStatus;

    private HeaderBanner headerBanner;

    private static final Logger LOG = Logger.getLogger(BaseActivity.class);

    public BaseActivity(int layoutResId) {
        super(layoutResId);
    }

    @Override
    protected void initToolbar(Toolbar toolbar) {
        View v = LayoutInflater.from(this).
                inflate(R.layout.view_toolbar_title_subtitle_header, toolbar, false);
        setToolbarView(v);

        TextView title = findView(R.id.view_toolbar_header_title);
        title.setText(R.string.my_music);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        headerBanner = findViewById(R.id.activity_base_header_banner);
        HeaderBanner.onResumeHideOrUpdate(headerBanner);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Bind Apollo's service
        //mToken = MusicUtils.bindToService(this, this);
        // Initialize the broadcast receiver
        mPlaybackStatus = new PlaybackStatus(this);
        // Initialize the bottom action bar
        initBottomActionBar();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        getMenuInflater().inflate(R.menu.player_search, menu);

        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_player_search).getActionView();
        // Add voice search
        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());

        if (searchView != null) { // rare NPE can occur
            searchView.setSearchableInfo(searchableInfo);
            // Perform the search
            searchView.setOnQueryTextListener(new OnQueryTextListener() {

                @Override
                public boolean onQueryTextSubmit(final String query) {
                    // Open the search activity - This is invoked when searching inside the My Music section
                    NavUtils.openSearch(BaseActivity.this, query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(final String newText) {
                    // Nothing to do
                    return false;
                }
            });
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return switch (item.getItemId()) {
            case android.R.id.home -> {
                getBackHome();
                yield true;
            }
            case R.id.menu_player_new_playlist -> {
                onOptionsItemNewPlaylistSelected();
                yield true;
            }
            default -> super.onOptionsItemSelected(item);
        };
    }

    protected void onOptionsItemNewPlaylistSelected() {
        long[] tracks = null;
        if (getIntent() != null && getIntent().hasExtra(Config.TRACKS)) {
            tracks = getIntent().getLongArrayExtra(Config.TRACKS);
        }
        CreateNewPlaylistMenuAction createPlaylistAction = new CreateNewPlaylistMenuAction(this, tracks);
        createPlaylistAction.onClick();
        MusicUtils.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        HeaderBanner.onResumeHideOrUpdate(headerBanner);
        // Set the playback drawables
        initBottomActionBar();
        updatePlaybackControls();
        // Current info
        updateBottomActionBarInfo();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();
        final IntentFilter filter = getIntentFilter();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mPlaybackStatus, filter, RECEIVER_EXPORTED);
            } else {
                registerReceiver(mPlaybackStatus, filter);
            }
        } catch (Throwable t) {
            LOG.error("BaseActivity("+getClass().getSimpleName()+") " + t.getMessage(), t);
        }

        // We ask here because we don't yet need to ask for the music service to be started.
        // On AudioPlayerActivity, it's another story, if we get there, it's because
        // we're playing a track.
        if (MusicUtils.isPlaying()) {
            MusicUtils.notifyForegroundStateChanged(this, true);
        }
    }

    @NonNull
    private static IntentFilter getIntentFilter() {
        final IntentFilter filter = new IntentFilter();
        // Play and pause changes
        filter.addAction(MusicPlaybackService.PLAYSTATE_CHANGED);
        // Shuffle and repeat changes
        filter.addAction(MusicPlaybackService.SHUFFLEMODE_CHANGED);
        filter.addAction(MusicPlaybackService.REPEATMODE_CHANGED);
        // Track changes
        filter.addAction(MusicPlaybackService.META_CHANGED);
        // Update a list, probably the playlist fragment's
        filter.addAction(MusicPlaybackService.REFRESH);
        return filter;
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        HeaderBanner.destroy(headerBanner);
        // Unregister the receiver
        try {
            unregisterReceiver(mPlaybackStatus);
        } catch (final Throwable e) {
            //$FALL-THROUGH$
        }

        // Remove any music status listeners
        mMusicStateListener.clear();
    }

    /**
     * Initializes the items in the bottom action bar.
     */
    private void initBottomActionBar() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            boolean isStopped = MusicUtils.isStopped();
            SystemUtils.postToUIThread(() -> initBottomActionBarPost(isStopped));
        });
    }

    private void initBottomActionBarPost(Boolean isStopped) {
        if (!isStopped) {
            // Play and pause button
            mPlayPauseButton = findViewById(R.id.action_button_play);
            mPlayPauseButton.setPlayDrawable(R.drawable.btn_playback_play_bottom);
            mPlayPauseButton.setPauseDrawable(R.drawable.btn_playback_pause_bottom);
            mPlayPauseButton.updateState();

            RepeatingImageButton prevButton = findViewById(R.id.action_button_previous);
            RepeatingImageButton nextButton = findViewById(R.id.action_button_next);
            prevButton.setPreviousDrawable(R.drawable.btn_playback_previous_bottom);
            nextButton.setNextDrawable(R.drawable.btn_playback_next_bottom);

            // Shuffle button
            mShuffleButton = findViewById(R.id.action_button_shuffle);
            // Repeat button
            mRepeatButton = findViewById(R.id.action_button_repeat);
            // Track name
            mTrackName = findViewById(R.id.bottom_action_bar_line_one);
            // Artist name
            mArtistName = findViewById(R.id.bottom_action_bar_line_two);
            // Album art
            mAlbumArt = findViewById(R.id.bottom_action_bar_album_art);
            // Open to the currently playing album profile
            mAlbumArt.setOnClickListener(mOpenCurrentAlbumProfile);
            // Bottom action bar
            final LinearLayout bottomActionBar = findViewById(R.id.bottom_action_bar);
            // Display the now playing screen or shuffle if this isn't anything
            // playing
            bottomActionBar.setOnClickListener(mOpenNowPlaying);

            mPlayPauseButton.setOnLongClickListener(new StopAndHideBottomActionBarListener(this, false));
        }
        setBottomActionBarVisible(!isStopped);
    }

    /**
     * Sets the track name, album name, and album art.
     */
    private void updateBottomActionBarInfo() {
        if (!MusicUtils.isStopped()) {
            if (mTrackName == null && mArtistName == null) {
                initBottomActionBar();
            }
            if (mTrackName == null || mArtistName == null) {
                return;
            }
            // Set the track name
            mTrackName.setText(MusicUtils.getTrackName());
            // Set the artist name
            mArtistName.setText(MusicUtils.getArtistName());
            // Set the album art
            ImageFetcher imageFetcher = ApolloUtils.getImageFetcher(this);
            imageFetcher.loadCurrentArtwork(mAlbumArt);
        }
    }

    private void setBottomActionBarVisible(boolean visible) {
        View bottomActionBar = findViewById(R.id.bottom_action_bar_parent);
        bottomActionBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets the correct drawable states for the playback controls.
     */
    private void updatePlaybackControls() {
        boolean showControls = !MusicUtils.isStopped() && mPlayPauseButton != null && mShuffleButton != null && mRepeatButton != null;
        if (showControls) {
            // Set the play and pause image
            mPlayPauseButton.updateState();
            // Set the shuffle image
            mShuffleButton.updateShuffleState();
            // Set the repeat image
            mRepeatButton.updateRepeatState();
        }
        setBottomActionBarVisible(!MusicUtils.isStopped());
    }

    /**
     * Opens the album profile of the currently playing album
     */
    private final View.OnClickListener mOpenCurrentAlbumProfile = new View.OnClickListener() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClick(final View v) {
            long currentAlbumId = MusicUtils.getCurrentAudioId();
            if (currentAlbumId != -1) {
                NavUtils.openAlbumProfile(BaseActivity.this,
                        MusicUtils.getAlbumName(),
                        MusicUtils.getArtistName(),
                        MusicUtils.getCurrentAlbumId(),
                        MusicUtils.getSongListForAlbum(BaseActivity.this, currentAlbumId));
            } else {
                MusicUtils.shuffleAll(BaseActivity.this);
            }
            if (BaseActivity.this instanceof ProfileActivity) {
                finish();
            }
        }
    };

    /**
     * Opens the now playing screen
     */
    private final View.OnClickListener mOpenNowPlaying = new View.OnClickListener() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onClick(final View v) {
            if (MusicUtils.getCurrentAudioId() != -1) {
                NavUtils.openAudioPlayer(BaseActivity.this);
            } else {
                MusicUtils.shuffleAll(BaseActivity.this);
            }
        }
    };

    /**
     * Used to monitor the state of playback
     */
    private final static class PlaybackStatus extends BroadcastReceiver {

        private final WeakReference<BaseActivity> mReference;

        /**
         * Constructor of <code>PlaybackStatus</code>
         */
        public PlaybackStatus(final BaseActivity activity) {
            mReference = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            switch (Objects.requireNonNull(action)) {
                case MusicPlaybackService.META_CHANGED -> {
                    // Current info
                    mReference.get().updateBottomActionBarInfo();
                    // Update the favorites icon
                    mReference.get().invalidateOptionsMenu();
                    // Let the listener know to the meta changed
                    for (final MusicStateListener listener : mReference.get().mMusicStateListener) {
                        if (listener != null) {
                            listener.onMetaChanged();
                        }
                    }
                }
                case MusicPlaybackService.PLAYSTATE_CHANGED -> {
                    // Set the play and pause image
                    if (!MusicUtils.isStopped() && mReference.get() != null && mReference.get().mPlayPauseButton != null) {
                        mReference.get().mPlayPauseButton.updateState();
                    }
                }
                case MusicPlaybackService.REPEATMODE_CHANGED,
                     MusicPlaybackService.SHUFFLEMODE_CHANGED -> {
                    if (!MusicUtils.isStopped() && mReference.get() != null && mReference.get().mRepeatButton != null &&
                            mReference.get().mShuffleButton != null) {
                        // Set the repeat image
                        mReference.get().mRepeatButton.updateRepeatState();
                        // Set the shuffle image
                        mReference.get().mShuffleButton.updateShuffleState();
                    }
                }
                case MusicPlaybackService.REFRESH -> {
                    // Let the listener know to update a list
                    for (final MusicStateListener listener : mReference.get().mMusicStateListener) {
                        if (listener != null) {
                            listener.restartLoader();
                        }
                    }
                }
            }
        }
    }

    /**
     * @param status The {@link MusicStateListener} to use
     */
    public void setMusicStateListenerListener(final MusicStateListener status) {
        if (status != null) {
            mMusicStateListener.add(status);
        }
    }

    // TODO: review this method
    private void getBackHome() {
        if (isTaskRoot()) {
            UIUtils.goToFrostWireMainActivity(this);
        } else {
            finish();
        }
    }

    private class StopAndHideBottomActionBarListener extends StopListener {

        StopAndHideBottomActionBarListener(Activity activity, boolean finishOnStop) {
            super(activity, finishOnStop);
        }

        @Override
        public boolean onLongClick(View v) {
            super.onLongClick(v);
            setBottomActionBarVisible(false);
            return true;
        }
    }
}
