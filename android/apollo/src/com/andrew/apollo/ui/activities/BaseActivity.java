/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.activities;

import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.andrew.apollo.Config;
import com.andrew.apollo.IApolloService;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.MusicStateListener;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.MusicUtils.ServiceToken;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.PlayPauseButton;
import com.andrew.apollo.widgets.RepeatButton;
import com.andrew.apollo.widgets.RepeatingImageButton;
import com.andrew.apollo.widgets.ShuffleButton;
import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.menu.CreateNewPlaylistMenuAction;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.util.WriteSettingsPermissionActivityHelper;
import com.frostwire.android.gui.views.AbstractActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static com.andrew.apollo.utils.MusicUtils.musicPlaybackService;

/**
 * A base {@link Activity} used to update the bottom bar and
 * bind to Apollo's service.
 * <p>
 * {@link HomeActivity} extends from this skeleton.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class BaseActivity extends AbstractActivity
        implements ServiceConnection {
    /**
     * Play state and meta change listener
     */
    private final ArrayList<MusicStateListener> mMusicStateListener = new ArrayList<>();

    /**
     * The service token
     */
    private ServiceToken mToken;

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
        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);
        // Initialize the broadcast receiver
        mPlaybackStatus = new PlaybackStatus(this);
        // Initialize the bottom action bar
        initBottomActionBar();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DangerousPermissionsChecker.WRITE_SETTINGS_PERMISSIONS_REQUEST_CODE) {
            WriteSettingsPermissionActivityHelper helper = new WriteSettingsPermissionActivityHelper(this);
            if (helper.onActivityResult(this, requestCode)) {
                return;
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        musicPlaybackService = IApolloService.Stub.asInterface(service);
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        updateBottomActionBarInfo();
        // Update the favorites icon
        invalidateOptionsMenu();
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        musicPlaybackService = null;
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
                    // Open the search activity
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
        switch (item.getItemId()) {
            case android.R.id.home:
                getBackHome();
                return true;
            case R.id.menu_player_new_playlist:
                onOptionsItemNewPlaylistSelected();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
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
        // Set the playback drawables
        initBottomActionBar();
        updatePlaybackControls();
        // Current info
        updateBottomActionBarInfo();
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        registerReceiver(mPlaybackStatus, filter);
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mToken != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }

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
        boolean isPlaying = !MusicUtils.isStopped();
        if (isPlaying) {
            // Play and pause button
            mPlayPauseButton = findViewById(R.id.action_button_play);
            mPlayPauseButton.setPlayDrawable(R.drawable.btn_playback_play_bottom);
            mPlayPauseButton.setPauseDrawable(R.drawable.btn_playback_pause_bottom);

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
        setBottomActionBarVisible(isPlaying);
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
            if (imageFetcher != null) {
                imageFetcher.loadCurrentArtwork(mAlbumArt);
            }
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
            if (action.equals(MusicPlaybackService.META_CHANGED)) {
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
            } else if (action.equals(MusicPlaybackService.PLAYSTATE_CHANGED)) {
                // Set the play and pause image
                if (!MusicUtils.isStopped() && mReference.get() != null && mReference.get().mPlayPauseButton != null) {
                    mReference.get().mPlayPauseButton.updateState();
                }
            } else if (action.equals(MusicPlaybackService.REPEATMODE_CHANGED)
                    || action.equals(MusicPlaybackService.SHUFFLEMODE_CHANGED)) {
                if (!MusicUtils.isStopped() && mReference.get() != null && mReference.get().mRepeatButton != null &&
                        mReference.get().mShuffleButton != null) {
                    // Set the repeat image
                    mReference.get().mRepeatButton.updateRepeatState();
                    // Set the shuffle image
                    mReference.get().mShuffleButton.updateShuffleState();
                }
            } else if (action.equals(MusicPlaybackService.REFRESH)) {
                // Let the listener know to update a list
                for (final MusicStateListener listener : mReference.get().mMusicStateListener) {
                    if (listener != null) {
                        listener.restartLoader();
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
