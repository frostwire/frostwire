/*
 * Copyright (C) 2012 Andrew Neal
 * Copyright (c) 2012-2020, FrostWire(R)
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Audio.Artists;
import android.provider.MediaStore.Audio.Playlists;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager.widget.ViewPager;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.adapters.PagerAdapter;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.ui.fragments.QueueFragment;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.PlayPauseButton;
import com.andrew.apollo.widgets.RepeatButton;
import com.andrew.apollo.widgets.RepeatingImageButton;
import com.andrew.apollo.widgets.ShuffleButton;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.adapters.menu.AddToPlaylistMenuAction;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.util.WriteSettingsPermissionActivityHelper;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.android.offers.MoPubAdNetwork;
import com.frostwire.android.offers.MopubBannerView;
import com.frostwire.android.offers.Offers;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.TaskThrottle;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.frostwire.android.util.Asyncs.async;

/**
 * Apollo's "now playing" interface.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class AudioPlayerActivity extends AbstractActivity implements
        OnSeekBarChangeListener,
        DeleteDialog.DeleteDialogCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final Logger LOG = Logger.getLogger(AudioPlayerActivity.class);

    // Message to refresh the time
    private static final int REFRESH_TIME = 500;
    private static final long MUSIC_SERVICE_REQUEST_TASK_REFRESH_INTERVAL_IN_MS = 150;
    private static final long UPDATE_NOW_PLAYING_INFO_REFRESH_INTERVAL_MS = 200;

    private static AtomicBoolean waitingToInitAlbumArtBanner = new AtomicBoolean();

    // Play and pause button
    private PlayPauseButton mPlayPauseButton;

    // Repeat button
    private RepeatButton mRepeatButton;

    // Shuffle button
    private ShuffleButton mShuffleButton;

    // Track name
    private TextView mTrackName;

    // Artist name
    private TextView mArtistName;

    // Album art
    private ImageView mAlbumArt;

    // MoPub Banner
    private MopubBannerView mMopubBannerView;
    private MopubBannerView.OnBannerDismissedListener mopubBannerDismissedListener;

    // Tiny artwork
    private ImageView mAlbumArtSmall;

    // Current time
    private TextView mCurrentTime;

    // Total time
    private TextView mTotalTime;

    // Queue switch
    private ImageView mQueueSwitch;

    // Progress
    private SeekBar mProgress;

    // Broadcast receiver
    private PlaybackStatus mPlaybackStatus;

    // Handler used to update the current time
    private TimeHandler mTimeHandler;

    // Pager adapter
    private PagerAdapter mPagerAdapter;

    // ViewPager container
    private FrameLayout mPageContainer;

    // Header
    private LinearLayout mAudioPlayerHeader;

    // Image cache
    private ImageFetcher mImageFetcher;

    private long mPosOverride = -1;

    private long mStartSeekPos = 0;

    private long mLastSeekEventTime;

    private long mLastShortSeekEventTime;

    private long lastInitAlbumArtBanner;

    private boolean mIsPaused = false;

    private boolean mFromTouch = false;
    private WriteSettingsPermissionActivityHelper writeSettingsHelper;
    private GestureDetector gestureDetector;

    private long lastProgressBarTouched;
    private long lastKnownPosition = 0;
    private long lastKnownDuration = 0;
    private boolean lastKnownIsPlaying = false;
    private String lastTrackName;
    private String lastArtistName;
    private String lastAlbumName;
    private String lastArtistAndAlbumNames;
    private long lastTrackId = -1;

    public AudioPlayerActivity() {
        super(R.layout.activity_player_base);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (Throwable t) {
            LOG.error("onCreate() (handled) " + t.getMessage(), t);
        }

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Initialize the image fetcher/cache
        mImageFetcher = ApolloUtils.getImageFetcher(this);

        // Initialize the handler used to update the current time
        mTimeHandler = new TimeHandler(this);

        // Initialize the broadcast receiver
        mPlaybackStatus = new PlaybackStatus(this);

        // Cache all the items
        initPlaybackControls();
        initMopubBannerView();

        // Album Art Ad Controls
        if (mPlayPauseButton != null) {
            mPlayPauseButton.setOnLongClickListener(new StopListener(this, true));
        }

        PlayerGestureListener gestureListener = new PlayerGestureListener();
        gestureDetector = new GestureDetector(this, gestureListener);
        gestureDetector.setOnDoubleTapListener(gestureListener);
        if (mAlbumArt != null) {
            mAlbumArt.setOnTouchListener(gestureListener);
        }

        writeSettingsHelper = new WriteSettingsPermissionActivityHelper(this);

        waitingToInitAlbumArtBanner.set(false);

        loadCurrentAlbumArt();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startPlayback();
    }

    @Override
    public void onProgressChanged(final SeekBar bar, final int progress, final boolean fromUser) {
        if (!fromUser || MusicUtils.getMusicPlaybackService() == null) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        lastProgressBarTouched = now;
        lastKnownDuration = MusicUtils.duration();
        if (now - mLastSeekEventTime > 250) {
            mLastSeekEventTime = now;
            mLastShortSeekEventTime = now;
            mPosOverride = lastKnownDuration * progress / 1000;
            MusicUtils.seek(mPosOverride);
            refreshCurrentTime(mFromTouch);
            if (!mFromTouch) {
                mPosOverride = -1;
            }
        } else if (now - mLastShortSeekEventTime > 5) {
            mLastShortSeekEventTime = now;
            mPosOverride = lastKnownDuration * progress / 1000;
            refreshCurrentTimeText(mPosOverride);
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar bar) {
        mLastSeekEventTime = 0;
        mFromTouch = true;
        mCurrentTime.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStopTrackingTouch(final SeekBar bar) {
        if (mPosOverride != -1) {
            MusicUtils.seek(mPosOverride);
        }
        mPosOverride = -1;
        mFromTouch = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        // Hide the EQ option if it can't be opened
        final Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (getPackageManager().resolveActivity(intent, 0) == null) {
            final MenuItem effects = menu.findItem(R.id.menu_player_audio_player_equalizer);
            if (effects != null) {
                effects.setVisible(false);
            }
        }
        MenuItem favoriteMenuItem = menu.findItem(R.id.menu_player_favorite);
        if (favoriteMenuItem != null) {
            favoriteMenuItem.setIcon(MusicUtils.isFavorite() ?
                    R.drawable.ic_action_favorite_selected : R.drawable.ic_action_favorite);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.player_search, menu);

        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_player_search).getActionView();
        // Add voice search
        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            final SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
            searchView.setSearchableInfo(searchableInfo);
            // Perform the search
            searchView.setOnQueryTextListener(new OnQueryTextListener() {

                @Override
                public boolean onQueryTextSubmit(final String query) {
                    // Open the search activity
                    NavUtils.openSearch(AudioPlayerActivity.this, query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(final String newText) {
                    // Nothing to do
                    return false;
                }
            });
        }

        // Favorite action
        menuInflater.inflate(R.menu.player_favorite, menu);
        // Share, ringtone, and equalizer
        menuInflater.inflate(R.menu.player_audio_player, menu);
        // Shuffle all
        menuInflater.inflate(R.menu.player_shuffle, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_player_shuffle:
                // Shuffle all the songs
                MusicUtils.shuffleAll(this);
                // Refresh the queue
                ((QueueFragment) mPagerAdapter.getFragment(0)).refreshQueue();
                return true;
            case R.id.menu_player_favorite:
                // Toggle the current track as a favorite and update the menu
                // item
                toggleFavorite();
                return true;
            case R.id.menu_player_audio_player_ringtone:
                // Set the current track as a ringtone
                writeSettingsHelper.onSetRingtoneOption(this, MusicUtils.getCurrentAudioId(), Constants.FILE_TYPE_AUDIO);
                return true;
            case R.id.menu_player_audio_player_share:
                // Share the current meta data
                shareCurrentTrack();
                return true;
            case R.id.menu_player_audio_player_equalizer:
                // Sound effects
                NavUtils.openEffectsPanel(this);
                return true;
            case R.id.menu_player_audio_player_stop:
                try {
                    MusicUtils.getMusicPlaybackService().stop();
                } catch (Throwable e) {
                    // ignore
                }
                finish();
                return true;
            case R.id.menu_player_audio_player_delete:
                // Delete current song
                DeleteDialog.newInstance(MusicUtils.getTrackName(), new long[]{
                        MusicUtils.getCurrentAudioId()
                }, null).show(getFragmentManager(), "DeleteDialog");
                return true;
            case R.id.menu_player_audio_player_add_to_playlist:
                AddToPlaylistMenuAction menuAction = new AddToPlaylistMenuAction(this, new long[]{
                        MusicUtils.getCurrentAudioId()
                });
                menuAction.onClick();
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDelete(long[] ids) {
        ((QueueFragment) mPagerAdapter.getFragment(0)).refreshQueue();
        if (MusicUtils.getQueue().length == 0) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE &&
                data != null &&
                data.hasExtra(BuyActivity.EXTRA_KEY_PURCHASE_TIMESTAMP)) {
            // We (onActivityResult) are invoked before onResume()
            if (mMopubBannerView != null) {
                mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.ALL, false);
            }
            showAlbumArt();
            long removeAdsPurchaseTime = data.getLongExtra(BuyActivity.EXTRA_KEY_PURCHASE_TIMESTAMP, 0);
            LOG.info("onActivityResult: User just purchased something. removeAdsPurchaseTime=" + removeAdsPurchaseTime);
        } else if (!writeSettingsHelper.onActivityResult(this, requestCode)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent != null) {
            Uri dataUri = intent.getData();

            if (MusicUtils.getMusicPlaybackService() == null) {
                MusicUtils.startMusicPlaybackService(getApplicationContext(),
                        MusicUtils.buildStartMusicPlaybackServiceIntent(getApplicationContext()),
                        () -> onNewIntent(intent));
            } else {
                onNewIntent(intent);
            }
        }

        waitingToInitAlbumArtBanner.set(false);
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        updateNowPlayingInfo();
        // Refresh the queue
        ((QueueFragment) mPagerAdapter.getFragment(0)).refreshQueue();
        initPlaybackControls();
        initMopubBannerView();
        loadCurrentAlbumArt();
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
        safeRegisterReceiver(filter);
        // Refresh the current time
        final long next = refreshCurrentTime(false);
        queueNextRefresh(next);
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    private void safeRegisterReceiver(IntentFilter filter) {
        if (mPlaybackStatus == null || !mPlaybackStatus.refAlive()) {
            mPlaybackStatus = new PlaybackStatus(this);
        }
        try {
            registerReceiver(mPlaybackStatus, filter);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
        if (mImageFetcher != null) {
            mImageFetcher.flush();
        }
    }

    @SuppressWarnings("CatchMayIgnoreException")
    @Override
    protected void onDestroy() {
        super.onDestroy();

        mIsPaused = false;

        try {
            if (mMopubBannerView != null) {
                mMopubBannerView.destroy();
            }
        } catch (Throwable ignored) {
            LOG.error(ignored.getMessage(), ignored);
        }

        try {
            mTimeHandler.removeMessages(REFRESH_TIME);
        } catch (Throwable ignored) {
            LOG.error(ignored.getMessage(), ignored);
        }

        try {
            unregisterReceiver(mPlaybackStatus);
        } catch (final Throwable ignored) {
            LOG.error(ignored.getMessage(), ignored);
        }
    }

    /**
     * Initializes the items in the now playing screen
     */
    private void initPlaybackControls() {
        // ViewPager container
        mPageContainer = findView(R.id.audio_player_pager_container);
        // Theme the pager container background
        if (mPageContainer != null) {
            mPageContainer.setBackgroundResource(R.drawable.audio_player_pager_container);
        }

        // Now playing header
        mAudioPlayerHeader = findView(R.id.audio_player_header);
        if (mAudioPlayerHeader != null) {
            // Opens the currently playing album profile
            mAudioPlayerHeader.setOnClickListener(mOpenAlbumProfile);
        }

        // Used to hide the artwork and show the queue
        final FrameLayout mSwitch = findView(R.id.audio_player_switch);
        if (mSwitch != null) {
            mSwitch.setOnClickListener(mToggleHiddenPanel);
        }

        // Initialize the pager adapter
        mPagerAdapter = new PagerAdapter(this);
        // Queue
        mPagerAdapter.add(QueueFragment.class, null);

        // Initialize the ViewPager
        ViewPager mViewPager = findView(R.id.audio_player_pager);
        if (mViewPager != null) {
            // Attach the adapter
            mViewPager.setAdapter(mPagerAdapter);
            // Offscreen pager loading limit
            mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount() - 1);
        }
        // Play and pause button
        mPlayPauseButton = findView(R.id.action_button_play);
        // Shuffle button
        mShuffleButton = findView(R.id.action_button_shuffle);
        // Repeat button
        mRepeatButton = findView(R.id.action_button_repeat);
        if (mShuffleButton != null) {
            mShuffleButton.setOnClickedCallback(() -> mRepeatButton.updateRepeatState());
        }
        if (mRepeatButton != null) {
            mRepeatButton.setOnClickedCallback(() -> mShuffleButton.updateShuffleState());
        }
        // Previous button
        RepeatingImageButton mPreviousButton = findView(R.id.action_button_previous);
        // Next button
        RepeatingImageButton mNextButton = findView(R.id.action_button_next);
        if (mPreviousButton != null) {
            // Set the repeat listener for the previous button
            mPreviousButton.setRepeatListener(mRewindListener);
        }
        if (mNextButton != null) {
            // Set the repeat listener for the next button
            mNextButton.setRepeatListener(mFastForwardListener);
        }
        // Track name
        mTrackName = findView(R.id.audio_player_track_name);
        // Artist name
        mArtistName = findView(R.id.audio_player_artist_name);
        // Album art
        mAlbumArt = findView(R.id.audio_player_album_art);
        // MoPubBannerView
        initMopubBannerView();
        // Small album art
        mAlbumArtSmall = findView(R.id.audio_player_switch_album_art);
        // Current time
        mCurrentTime = findView(R.id.audio_player_current_time);
        // Total time
        mTotalTime = findView(R.id.audio_player_total_time);
        // Used to show and hide the queue fragment
        mQueueSwitch = findView(R.id.audio_player_switch_queue);
        // Theme the queue switch icon
        if (mQueueSwitch != null) {
            mQueueSwitch.setImageResource(R.drawable.btn_switch_queue);
        }
        // Progress
        mProgress = findView(android.R.id.progress);
        if (mProgress != null) {
            // Update the progress
            mProgress.setOnSeekBarChangeListener(this);
        }
        showAlbumArt();
        if (mMopubBannerView != null && !Offers.disabledAds()) {
            mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.FALLBACK, true);
        }
    }

    private void initMopubBannerView() {
        mMopubBannerView = findView(R.id.audio_player_mopub_banner_view);
        if (mMopubBannerView != null) {
            if (Offers.disabledAds()) {
                mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.ALL, false);
                return;
            }
            mMopubBannerView.setShowDismissButton(false);
            mMopubBannerView.loadFallbackBanner(MoPubAdNetwork.UNIT_ID_AUDIO_PLAYER);
            mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.FALLBACK, true);
            mMopubBannerView.setShowFallbackBannerOnDismiss(false);
            mMopubBannerView.setOnBannerLoadedListener(() -> mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.MOPUB, true));
            mMopubBannerView.setOnFallbackBannerLoadedListener(() -> mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.FALLBACK, true));
            deferredInitAlbumArtBanner();
        }
    }

    private void deferredInitAlbumArtBanner() {
        if (mMopubBannerView == null) {
            LOG.info("deferredInitAlbumArtBanner() mMopubBannerView is not ready or available for this layout (mMopubBannerView == null)");
            return;
        }

        if (Offers.disabledAds()) {
            LOG.info("deferredInitAlbumArtBanner() aborted, ads are disabled");
            waitingToInitAlbumArtBanner.set(false);
            return;
        }
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            LOG.info("deferredInitAlbumArtBanner() aborted, activity is not visible to user");
            return;
        }
        if (UIUtils.isScreenLocked(this)) {
            LOG.info("deferredInitAlbumArtBanner() aborting call to initAlbumArt, screen is locked");
            waitingToInitAlbumArtBanner.set(false);
            return;
        }

        updateLastKnown(MusicServiceRequestType.TRACK_ID, false); // throttled async call

        if (lastTrackId == -1) {
            LOG.info("deferredInitAlbumArtBanner() aborting call to initAlbumArt, lastTrackId=-1");
            waitingToInitAlbumArtBanner.set(false);
            return;
        }

        if (mMopubBannerView.areLayerVisible(MopubBannerView.Layers.MOPUB)) {
            LOG.info("deferredInitAlbumArtBanner() aborting call to initAlbumArt, ad is already visible");
            return;
        }

        if (waitingToInitAlbumArtBanner.get()) {

            if (!mMopubBannerView.areLayerVisible(MopubBannerView.Layers.FALLBACK)) {
                mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.FALLBACK, true);
            }

            LOG.info("deferredInitAlbumArtBanner() aborting call to initAlbumArt, already waiting");
            return;
        }

        // let's not leak ourselves
        waitingToInitAlbumArtBanner.set(true);

        //final WeakReference<IApolloService> musicPlaybackServiceWeakReference = Ref.weak(musicPlaybackService);
        final WeakReference<AudioPlayerActivity> activityRef = Ref.weak(this);

        final long trackIdWeAreMonitoring = lastTrackId;
        updateLastKnown(MusicServiceRequestType.IS_PLAYING, false);
        LOG.info("deferredInitAlbumArtBanner() about to postDelayed waiting for track " + trackIdWeAreMonitoring);
        mTimeHandler.postDelayed(() -> {

            LOG.info("deferredInitAlbumArtBanner() callback invoked!");

            // activity not there anymore, abort
            if (!Ref.alive(activityRef)) {
                LOG.info("deferredInitAlbumArtBanner() callback aborted, audio player activity gone");
                waitingToInitAlbumArtBanner.set(false);
                Ref.free(activityRef);
            }

            // current track is different, maybe call deferredInitAlbumArtBanner again? careful with stack overflow

            if (lastTrackId != trackIdWeAreMonitoring) {
                LOG.info("deferredInitAlbumArtBanner() callback aborted, on a different track. trackIdWeAreMonitoring=" + trackIdWeAreMonitoring + " currentTrack=" + lastTrackId);
                waitingToInitAlbumArtBanner.set(false);
                return;
            }

            // ads got disabled in the meantime
            if (Offers.disabledAds()) {
                LOG.info("deferredInitAlbumArtBanner() callback aborted, ads got disabled in between");
                waitingToInitAlbumArtBanner.set(false);
                return;
            }

            long position = activityRef.get().lastKnownPosition;
            final long minPassedPosition = 3000;
            if (lastKnownIsPlaying && position < minPassedPosition) {
                LOG.error("deferredInitAlbumArtBanner() callback failed: Position@" +
                        position + " < " + minPassedPosition + "??? THIS SHOULD NOT HAPPEN");
                return;
            }
            waitingToInitAlbumArtBanner.set(false);
            activityRef.get().initAlbumArtBanner();
        }, 8000);
    }


    private void initAlbumArtBanner() {
        if (UIUtils.isScreenLocked(this)) {
            LOG.info("initAlbumArtBanner() aborted, screen locked");
            return;
        }
        LOG.info("initAlbumArtBanner() invoked");
        if (mAlbumArt != null) {
            mAlbumArt.setVisibility(View.VISIBLE);
        }
        if (mMopubBannerView == null) {
            LOG.info("initAlbumArtBanner() aborted: mMopubBannerView == null");
            return;
        }
        if (Offers.disabledAds()) {
            LOG.info("initAlbumArtBanner() aborted: Ads are disabled");
            mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.ALL, false);
            return;
        }
        mMopubBannerView.loadMoPubBanner(MoPubAdNetwork.UNIT_ID_AUDIO_PLAYER);
    }

    /**
     * Sets the track name, album name, and album art.
     */
    private void updateNowPlayingInfo() {
        LOG.info("updateNowPlayingInfo() invoked", true);
        updateLastKnown(MusicServiceRequestType.TRACK_ID, true);
        updateLastKnown(MusicServiceRequestType.TRACK_NAME, true);
        updateLastKnown(MusicServiceRequestType.DURATION, true);
        updateLastKnown(MusicServiceRequestType.POSITION, true);
        updateLastKnown(MusicServiceRequestType.ARTIST_AND_ALBUM_NAMES, true);

        // Update the current time
        queueNextRefresh(UPDATE_NOW_PLAYING_INFO_REFRESH_INTERVAL_MS);
    }

    private void onLastKnownUpdatePostTask() {
        // Set the track name
        mTrackName.setText(lastTrackName);
        // Set the artist name
        mArtistName.setText(lastArtistAndAlbumNames);
        // Set the total time
        mTotalTime.setText(MusicUtils.makeTimeString(this, lastKnownDuration(false) / 1000));

        loadCurrentAlbumArt();
        updateQueueFragmentCurrentSong();
    }

    private void loadCurrentAlbumArt() {
        if (mImageFetcher != null) {
            // Set the album art
            if (mAlbumArt != null) {
                mImageFetcher.loadCurrentArtwork(mAlbumArt);
            }
            // Set the small artwork
            if (mAlbumArtSmall != null) {
                mImageFetcher.loadCurrentArtwork(mAlbumArtSmall);
            }
        }
    }

    private String getArtistAndAlbumName(String artist, String album) {
        String str = "";

        if (artist != null && album != null) {
            str = artist + " - " + album;
        } else if (artist != null) {
            str = artist;
        } else if (album != null) {
            str = album;
        }

        return str;
    }

    private long parseIdFromIntent(Intent intent, String longKey, String stringKey) {
        long id = intent.getLongExtra(longKey, -1);
        if (id < 0) {
            String idString = intent.getStringExtra(stringKey);
            if (idString != null) {
                try {
                    id = Long.parseLong(idString);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return id;
    }

    /**
     * Checks whether the passed intent contains a playback request,
     * and starts playback if that's the case
     */
    private void startPlayback() {
        Intent intent = getIntent();

        if (intent == null || MusicUtils.getMusicPlaybackService() == null) {
            return;
        }

        Uri uri = intent.getData();
        String mimeType = intent.getType();
        boolean handled = false;

        if (uri != null && uri.toString().length() > 0) {
            MusicUtils.playFileFromUri(uri);
            handled = true;
        } else if (Playlists.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "playlistId", "playlist");
            if (id >= 0) {
                MusicUtils.playPlaylist(this, id);
                handled = true;
            }
        } else if (Albums.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "albumId", "album");
            if (id >= 0) {
                int position = intent.getIntExtra("position", 0);
                MusicUtils.playAlbum(this, id, position);
                handled = true;
            }
        } else if (Artists.CONTENT_TYPE.equals(mimeType)) {
            long id = parseIdFromIntent(intent, "artistId", "artist");
            if (id >= 0) {
                int position = intent.getIntExtra("position", 0);
                MusicUtils.playArtist(this, id, position);
                handled = true;
            }
        }

        if (handled) {
            // Make sure to process intent only once
            setIntent(new Intent());
            // Refresh the queue
            ((QueueFragment) mPagerAdapter.getFragment(0)).refreshQueue();
        }
    }

    /**
     * Sets the correct drawable states for the playback controls.
     */
    private void updatePlaybackControls() {
        // Set the repeat image
        if (mRepeatButton != null) {
            mRepeatButton.updateRepeatState();
        }
        // Set the play and pause image
        if (mPlayPauseButton != null) {
            if (mPlaybackStatus != null && !mPlayPauseButton.hasOnLongClickListener()) {
                mPlayPauseButton.setOnLongClickListener(new StopListener(this, true));
            }
            mPlayPauseButton.updateState();
        }
        // Set the shuffle image
        if (mShuffleButton != null) {
            mShuffleButton.updateShuffleState();
        }
    }

    private void updateQueueFragmentCurrentSong() {
        QueueFragment qFragment = (QueueFragment) mPagerAdapter.getFragment(0);
        qFragment.notifyAdapterDataSetChanged();
    }

    /**
     * @param delay When to update
     */
    private void queueNextRefresh(final long delay) {
        if (!mIsPaused) {
            final Message message = mTimeHandler.obtainMessage(REFRESH_TIME);
            mTimeHandler.removeMessages(REFRESH_TIME);
            mTimeHandler.sendMessageDelayed(message, delay);
        }
    }

    /**
     * Used to scan backwards in time through the current track
     *
     * @param repcount The repeat count
     * @param delta    The long press duration
     */
    private void scanBackward(final int repcount, long delta) {
        if (MusicUtils.getMusicPlaybackService() == null) {
            return;
        }
        if (repcount == 0) {
            mStartSeekPos = MusicUtils.position();
            lastKnownPosition = mStartSeekPos;
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos - delta;
            if (newpos <= 0) {
                // move to previous track
                MusicUtils.previous();
                final long duration = MusicUtils.duration();
                mStartSeekPos += duration;
                newpos += duration;
            }
            if (delta - mLastSeekEventTime > 250 || repcount < 0) {
                MusicUtils.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcount >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshCurrentTime(true);
        }
    }

    /**
     * Used to scan forwards in time through the current track
     *
     * @param repcount The repeat count
     * @param delta    The long press duration
     */
    private void scanForward(final int repcount, long delta) {
        if (MusicUtils.getMusicPlaybackService() == null) {
            return;
        }
        if (repcount == 0) {
            lastKnownPosition = MusicUtils.position();
            mStartSeekPos = lastKnownPosition;
            mLastSeekEventTime = 0;
        } else {
            if (delta < 5000) {
                // seek at 10x speed for the first 5 seconds
                delta = delta * 10;
            } else {
                // seek at 40x after that
                delta = 50000 + (delta - 5000) * 40;
            }
            long newpos = mStartSeekPos + delta;
            long duration = lastKnownDuration(true);
            if (newpos >= duration) {
                // move to next track
                MusicUtils.next();
                duration = MusicUtils.duration();
                lastKnownDuration = duration;
                mStartSeekPos -= duration; // is OK to go negative
                newpos -= duration;
            }
            if (delta - mLastSeekEventTime > 250 || repcount < 0) {
                MusicUtils.seek(newpos);
                mLastSeekEventTime = delta;
            }
            if (repcount >= 0) {
                mPosOverride = newpos;
            } else {
                mPosOverride = -1;
            }
            refreshCurrentTime(true);
        }
    }

    private void refreshCurrentTimeText(final long pos) {
        mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
    }

    enum MusicServiceRequestType {
        POSITION,
        DURATION,
        IS_PLAYING,
        TRACK_ID,
        TRACK_NAME,
        ARTIST_NAME,
        ALBUM_NAME,
        ARTIST_AND_ALBUM_NAMES,
    }

    private static void musicServiceRequestTask(AudioPlayerActivity activity,
                                                MusicServiceRequestType requestType) {
        switch (requestType) {
            case POSITION:
                activity.lastKnownPosition = MusicUtils.position();
                break;
            case DURATION:
                activity.lastKnownDuration = MusicUtils.duration();
                break;
            case IS_PLAYING:
                activity.lastKnownIsPlaying = MusicUtils.isPlaying();
                break;
            case TRACK_NAME:
                activity.lastTrackName = MusicUtils.getTrackName();
                break;
            case ARTIST_NAME:
                activity.lastArtistName = MusicUtils.getArtistName();
                break;
            case ALBUM_NAME:
                activity.lastAlbumName = MusicUtils.getAlbumName();
                break;
            case ARTIST_AND_ALBUM_NAMES:
                activity.lastArtistName = MusicUtils.getArtistName();
                activity.lastAlbumName = MusicUtils.getAlbumName();
                activity.lastArtistAndAlbumNames = activity.getArtistAndAlbumName(activity.lastArtistName, activity.lastAlbumName);
                break;
            case TRACK_ID:
                activity.lastTrackId = MusicUtils.getCurrentAudioId();
                break;
            default:
                break;
        }
    }

    private void updateLastKnown(MusicServiceRequestType requestType, boolean callOnLastKnownUpdatePostTaskAfter) {
        if (TaskThrottle.isReadyToSubmitTask("AudioPlayerActivity::musicServiceRequestTask(" + requestType.name() + ")", MUSIC_SERVICE_REQUEST_TASK_REFRESH_INTERVAL_IN_MS)) {
            if (callOnLastKnownUpdatePostTaskAfter) {
                async(this, AudioPlayerActivity::musicServiceRequestTask, requestType, AudioPlayerActivity::onLastKnownUpdatePostTask);
            } else {
                async(this, AudioPlayerActivity::musicServiceRequestTask, requestType);
            }
        }
    }

    private long lastKnownPosition(boolean blockingMusicServiceRequest) {
        if (!blockingMusicServiceRequest) {
            updateLastKnown(MusicServiceRequestType.POSITION, false);
        } else {
            lastKnownPosition = MusicUtils.position();
        }
        return lastKnownPosition;
    }

    private long lastKnownDuration(boolean blockingMusicServiceRequest) {
        if (!blockingMusicServiceRequest) {
            updateLastKnown(MusicServiceRequestType.DURATION, false);
        } else {
            lastKnownDuration = MusicUtils.duration();
        }
        return lastKnownDuration;
    }

    private boolean lastKnownIsPlaying(boolean blockingMusicServiceRequest) {
        if (!blockingMusicServiceRequest) {
            updateLastKnown(MusicServiceRequestType.IS_PLAYING, false);
        } else {
            lastKnownIsPlaying = MusicUtils.isPlaying();
        }
        return lastKnownIsPlaying;
    }

    /* Used to update the current time string
     *  @return the delay on which this method call should be posted again
     * */
    @SuppressWarnings("CatchMayIgnoreException")
    private long refreshCurrentTime(boolean blockingMusicServiceRequest) {
        if (MusicUtils.getMusicPlaybackService() == null) {
            return 500L;
        }

        try {
            final long pos = mPosOverride < 0 ? lastKnownPosition(blockingMusicServiceRequest) : mPosOverride;
            long duration = lastKnownDuration(false);
            if (pos >= 0 && duration > 0) {
                refreshCurrentTimeText(pos);
                final int progress = (int) (1000 * pos / duration);
                mProgress.setProgress(progress);

                if (mFromTouch) {
                    return 500L;
                } else if (lastKnownIsPlaying(blockingMusicServiceRequest)) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    final int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE
                            : View.INVISIBLE);
                    return 500L;
                }
            } else {
                mCurrentTime.setText("--:--");
                mProgress.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second,
            // so the counter can be updated at just the right time
            final long remaining = 1000 - pos % 1000;
            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mProgress.getWidth();
            if (width == 0) {
                width = 320;
            }
            final long smooth_refresh_time = duration / width;
            if (smooth_refresh_time > remaining) {
                return remaining;
            }
            if (smooth_refresh_time < 20) {
                return 20L;
            }
            return smooth_refresh_time;
        } catch (final Exception ignored) {
            LOG.error(ignored.getMessage(), ignored);
        }
        return 500L;
    }

    /**
     * @param v     The view to animate
     * @param alpha The alpha to apply
     */
    private void fade(final View v, final float alpha) {
        final ObjectAnimator fade = ObjectAnimator.ofFloat(v, "alpha", alpha);
        fade.setInterpolator(AnimationUtils.loadInterpolator(this,
                android.R.anim.accelerate_decelerate_interpolator));
        fade.setDuration(400);
        fade.start();
    }

    /**
     * Called to show the album art and hide the queue
     */
    private void showAlbumArt() {
        if (mPageContainer != null) {
            mPageContainer.setVisibility(View.INVISIBLE);
        }
        if (mAlbumArtSmall != null) {
            mAlbumArtSmall.setVisibility(View.GONE);
        }
        if (mQueueSwitch != null) {
            mQueueSwitch.setVisibility(View.VISIBLE);
        }
        if (mPageContainer != null) {
            // Fade out the pager container
            fade(mPageContainer, 0f);
        }
        if (mAlbumArt != null) {
            // Fade in the album art
            fade(mAlbumArt, 1f);
        }
    }

    /**
     * Called to hide the album art and show the queue
     */
    private void hideAlbumArt() {
        mPageContainer.setVisibility(View.VISIBLE);
        mQueueSwitch.setVisibility(View.GONE);
        mAlbumArtSmall.setVisibility(View.VISIBLE);
        // Fade out the artwork
        fade(mAlbumArt, 0f);
        // Fade in the pager container
        fade(mPageContainer, 1f);
    }

    /**
     * /** Used to shared what the user is currently listening to
     */
    private void shareCurrentTrack() {
        if (mMopubBannerView != null) {
            mMopubBannerView.setLayersVisibility(MopubBannerView.Layers.ALL, false);
        }
        async(this, AudioPlayerActivity::shareTrackScreenshotTask);
    }

    private static void shareTrackScreenshotTask(AudioPlayerActivity activity) {
        final long currentAudioId = MusicUtils.getCurrentAudioId();
        final String trackName = MusicUtils.getTrackName();
        if (currentAudioId == -1 || trackName == null) {
            return;
        }
        final Intent shareIntent = new Intent();
        final String artistName = MusicUtils.getArtistName();
        final String shareMessage = (artistName != null) ? activity.getString(R.string.now_listening_to, trackName, artistName) :
                activity.getString(R.string.now_listening_to_no_artist_available, trackName);
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
        View rootView = activity.getWindow().getDecorView().getRootView();
        File screenshotFile = UIUtils.takeScreenshot(rootView);
        if (screenshotFile != null && screenshotFile.canRead() && screenshotFile.length() > 0) {
            shareIntent.setType("image/jpg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, UIUtils.getFileUri(activity, screenshotFile.getAbsolutePath()));
        } else {
            shareIntent.setType("text/plain");
        }
        activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share_track_using)));
    }

    private void toggleFavorite() {
        MusicUtils.toggleFavorite();
        invalidateOptionsMenu();
    }

    /**
     * Used to scan backwards through the track
     */
    private final RepeatingImageButton.RepeatListener mRewindListener = (v, howlong, repcnt) -> scanBackward(repcnt, howlong);

    /**
     * Used to scan ahead through the track
     */
    private final RepeatingImageButton.RepeatListener mFastForwardListener = (v, howlong, repcnt) -> scanForward(repcnt, howlong);

    /**
     * Switches from the large album art screen to show the queue and lyric
     * fragments, then back again
     */
    private final OnClickListener mToggleHiddenPanel = new OnClickListener() {
        @Override
        public void onClick(final View v) {
            if (mPageContainer.getVisibility() == View.VISIBLE) {
                // Open the current album profile
                mAudioPlayerHeader.setOnClickListener(mOpenAlbumProfile);
                // Show the artwork, hide the queue
                showAlbumArt();
            } else {
                // Scroll to the current track
                mAudioPlayerHeader.setOnClickListener(mScrollToCurrentSong);
                // Show the queue, hide the artwork
                hideAlbumArt();
            }
        }
    };

    /**
     * Opens to the current album profile
     */
    @SuppressWarnings("CatchMayIgnoreException")
    private final OnClickListener mOpenAlbumProfile = v -> {
        long albumId = MusicUtils.getCurrentAlbumId();
        try {
            NavUtils.openAlbumProfile(AudioPlayerActivity.this,
                    MusicUtils.getAlbumName(),
                    MusicUtils.getArtistName(),
                    albumId,
                    MusicUtils.getSongListForAlbum(AudioPlayerActivity.this, albumId));
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }
    };

    /**
     * Scrolls the queue to the currently playing song
     */
    private final OnClickListener mScrollToCurrentSong = new OnClickListener() {

        @Override
        public void onClick(final View v) {
            ((QueueFragment) mPagerAdapter.getFragment(0)).scrollToCurrentSong();
        }
    };

    /**
     * Used to update the current time string
     */
    private static final class TimeHandler extends Handler {

        private final WeakReference<AudioPlayerActivity> mAudioPlayer;

        /**
         * Constructor of <code>TimeHandler</code>
         */
        TimeHandler(final AudioPlayerActivity player) {
            mAudioPlayer = new WeakReference<>(player);
        }

        @Override
        public void handleMessage(@NonNull final Message msg) {
            if (!Ref.alive(mAudioPlayer)) {
                return;
            }
            AudioPlayerActivity audioPlayerActivity = mAudioPlayer.get();
            if (msg.what == REFRESH_TIME) {// we only refresh synchronously when the progress bar has been moved
                // by the user, to avoid the progress bar from jumping back and forth
                // otherwise, no need to wait for MusicService in the main thread to update
                // the UI.
                boolean blockingRefresh =
                        (SystemClock.elapsedRealtime() -
                                audioPlayerActivity.lastProgressBarTouched) < 500;
                long next = audioPlayerActivity.refreshCurrentTime(blockingRefresh);

                // a blocking refresh could take long and screen could rotate, or activity go away
                if (blockingRefresh && Ref.alive(mAudioPlayer)) {
                    audioPlayerActivity = mAudioPlayer.get();
                }
                if (!Ref.alive(mAudioPlayer)) {
                    return;
                }
                if (audioPlayerActivity != null) {
                    audioPlayerActivity.queueNextRefresh(next);
                }
            }
        }
    }

    /**
     * Used to monitor the state of playback
     */
    private static final class PlaybackStatus extends BroadcastReceiver {

        private final WeakReference<AudioPlayerActivity> mReference;

        /**
         * Constructor of <code>PlaybackStatus</code>
         */
        public PlaybackStatus(final AudioPlayerActivity activity) {
            mReference = Ref.weak(activity);
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (!Ref.alive(mReference)) {
                return;
            }
            AudioPlayerActivity activity = mReference.get();
            if (activity == null) {
                return;
            }
            final String action = intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case MusicPlaybackService.META_CHANGED:
                    // Current info
                    LOG.info("PlaybackStatus::onReceive(MusicPlaybackService.META_CHANGED)");
                    activity.updateNowPlayingInfo();
                    // Update the favorites icon
                    activity.invalidateOptionsMenu();
                    activity.updateQueueFragmentCurrentSong();
                    activity.deferredInitAlbumArtBanner();
                    break;
                case MusicPlaybackService.PLAYSTATE_CHANGED:
                    LOG.info("PlaybackStatus::onReceive(MusicPlaybackService.PLAYSTATE_CHANGED)");
                    // Set the play and pause image
                    if (activity.mPlayPauseButton != null) {
                        activity.mPlayPauseButton.updateState();
                    }
                    break;
                case MusicPlaybackService.REPEATMODE_CHANGED:
                case MusicPlaybackService.SHUFFLEMODE_CHANGED:
                    // Set the repeat image
                    if (activity.mRepeatButton != null) {
                        activity.mRepeatButton.updateRepeatState();
                    }
                    // Set the shuffle image
                    if (activity.mShuffleButton != null) {
                        activity.mShuffleButton.updateShuffleState();
                    }
                    break;
            }
        }

        public boolean refAlive() {
            return Ref.alive(mReference);
        }
    }

    private final class PlayerGestureListener extends SwipeLayout.SwipeGestureAdapter
            implements View.OnTouchListener {

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return true;
            }

            if (event.getPointerCount() == 2 &&
                    event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                onMultiTouchEvent();
                return true;
            }

            return false;
        }

        @Override
        public void onSwipeLeft() {
            try {
                MusicUtils.next();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void onSwipeRight() {
            try {
                MusicUtils.previous();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            try {
                toggleFavorite();
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        private void onMultiTouchEvent() {
            try {
                MusicUtils.playPauseOrResume();
            } catch (Throwable e) {
                // ignore
            }
        }
    }
}
