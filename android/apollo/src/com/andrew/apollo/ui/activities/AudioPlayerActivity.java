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

import android.animation.ObjectAnimator;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Audio.Artists;
import android.provider.MediaStore.Audio.Playlists;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.andrew.apollo.IApolloService;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.adapters.PagerAdapter;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.menu.DeleteDialog;
import com.andrew.apollo.ui.fragments.QueueFragment;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.MusicUtils.ServiceToken;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.widgets.PlayPauseButton;
import com.andrew.apollo.widgets.RepeatButton;
import com.andrew.apollo.widgets.RepeatingImageButton;
import com.andrew.apollo.widgets.ShuffleButton;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.adapters.menu.AddToPlaylistMenuAction;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.util.WriteSettingsPermissionActivityHelper;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.android.offers.InHouseBannerFactory;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

import java.lang.ref.WeakReference;

import static com.andrew.apollo.utils.MusicUtils.musicPlaybackService;

/**
 * Apollo's "now playing" interface.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class AudioPlayerActivity extends AbstractActivity implements
        ServiceConnection,
        OnSeekBarChangeListener,
        DeleteDialog.DeleteDialogCallback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    private static final Logger LOG = Logger.getLogger(AudioPlayerActivity.class);

    // Message to refresh the time
    private static final int REFRESH_TIME = 1;

    // The service token
    private ServiceToken mToken;

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

    // Ad on top of Album art
    private MoPubView mMopubAd;

    // Fallback Ad ImageView
    private ImageView mFallbackAd;

    private final MoPubView.BannerAdListener moPubBannerListener = new MoPubView.BannerAdListener() {
        @Override
        public void onBannerLoaded(MoPubView banner) {
            LOG.info("onBannerLoaded()");
            setBannerViewVisibility(mFallbackAd, false);
            setBannerViewVisibility(mMopubAd, true);
        }

        @Override
        public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode) {
            LOG.info("onBannerFailed");
            loadFallbackBanner();
        }

        @Override
        public void onBannerClicked(MoPubView banner) {
            LOG.info("onBannerClicked: " + banner);
        }

        @Override
        public void onBannerExpanded(MoPubView banner) {
        }

        @Override
        public void onBannerCollapsed(MoPubView banner) {
            LOG.info("onBannerCollapsed");
            setBannerViewVisibility(mMopubAd, false);
        }
    };

    private final OnClickListener onDismissAlbumArtAdButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            setBannerViewVisibility(mMopubAd, false);
            setBannerViewVisibility(mFallbackAd, false);
        }
    };

    // 'advertisement' label
    private TextView mAdvertisementText;

    // Button to dismiss album art
    private ImageButton mDismissAlbumArtAdButton;

    // Album art
    private ImageView mAlbumArt;

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

    // for removeAds display
    private long removeAdsPurchaseTime = 0;
    private final OnClickListener removeAdsTextViewOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            setBannerViewVisibility(mMopubAd, false);
            setBannerViewVisibility(mFallbackAd, false);
            PlayStore.getInstance().endAsync();
            Intent i = new Intent(AudioPlayerActivity.this, BuyActivity.class);
            startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        }
    };

    public AudioPlayerActivity() {
        super(R.layout.activity_player_base);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Control the media volume
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Bind Apollo's service
        mToken = MusicUtils.bindToService(this, this);

        // Initialize the image fetcher/cache
        mImageFetcher = ApolloUtils.getImageFetcher(this);

        // Initialize the handler used to update the current time
        mTimeHandler = new TimeHandler(this);

        // Initialize the broadcast receiver
        mPlaybackStatus = new PlaybackStatus(this);

        // Cache all the items
        initPlaybackControls();

        // Album Art Ad Controls
        mMopubAd = findView(R.id.audio_player_mopubview);
        mFallbackAd = findView(R.id.audio_player_fallback_imageview);
        mDismissAlbumArtAdButton = findView(R.id.audio_player_dismiss_mopubview_button);
        mAdvertisementText = findView(R.id.audio_player_advertisement_text);
        initAlbumArtBanner();
        initRemoveAds();

        mPlayPauseButton.setOnLongClickListener(new StopListener(this, true));

        PlayerGestureListener gestureListener = new PlayerGestureListener();
        gestureDetector = new GestureDetector(this, gestureListener);
        gestureDetector.setOnDoubleTapListener(gestureListener);
        findView(R.id.audio_player_album_art).setOnTouchListener(gestureListener);

        writeSettingsHelper = new WriteSettingsPermissionActivityHelper(this);
    }

    private void initRemoveAds() {
        TextView removeAdsTextView = findView(R.id.audio_player_remove_ads_text_link);

        if (!Offers.removeAdsOffersEnabled() || (removeAdsPurchaseTime > 0)) {
            removeAdsTextView.setVisibility(View.GONE);
            removeAdsTextView.setOnClickListener(null);
        } else {
            removeAdsTextView.setVisibility(View.GONE);
            removeAdsTextView.setClickable(true);
            removeAdsTextView.setOnClickListener(removeAdsTextViewOnClickListener);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        startPlayback();
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        musicPlaybackService = IApolloService.Stub.asInterface(service);
        // Check whether we were asked to start any playback
        startPlayback();
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        updateNowPlayingInfo();
        // Update the favorites icon
        invalidateOptionsMenu();
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
        musicPlaybackService = null;
    }

    @Override
    public void onProgressChanged(final SeekBar bar, final int progress, final boolean fromuser) {
        if (!fromuser || musicPlaybackService == null) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        if (now - mLastSeekEventTime > 250) {
            mLastSeekEventTime = now;
            mLastShortSeekEventTime = now;
            mPosOverride = MusicUtils.duration() * progress / 1000;
            MusicUtils.seek(mPosOverride);
            if (!mFromTouch) {
                // refreshCurrentTime();
                mPosOverride = -1;
            }
        } else if (now - mLastShortSeekEventTime > 5) {
            mLastShortSeekEventTime = now;
            mPosOverride = MusicUtils.duration() * progress / 1000;
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
            effects.setVisible(false);
        }
        menu.findItem(R.id.menu_player_favorite).setIcon(MusicUtils.isFavorite() ?
                R.drawable.ic_action_favorite_selected : R.drawable.ic_action_favorite);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Search view
        MenuInflater menuInflater = getMenuInflater();

        if (menuInflater == null) {
            return false;
        }

        menuInflater.inflate(R.menu.player_search, menu);

        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_player_search).getActionView();
        // Add voice search
        final SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
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

        // Favorite action
        menuInflater.inflate(R.menu.player_favorite, menu);
        // Shuffle all
        menuInflater.inflate(R.menu.player_shuffle, menu);
        // Share, ringtone, and equalizer
        menuInflater.inflate(R.menu.player_audio_player, menu);
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
                    MusicUtils.musicPlaybackService.stop();
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
            removeAdsPurchaseTime = data.getLongExtra(BuyActivity.EXTRA_KEY_PURCHASE_TIMESTAMP, 0);
            LOG.info("onActivityResult: User just purchased something. removeAdsPurchaseTime=" + removeAdsPurchaseTime);
        } else if (!writeSettingsHelper.onActivityResult(this, requestCode)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        Engine.instance().getVibrator().hapticFeedback();
        try {
            super.onBackPressed();
        } catch (Throwable ignored) {}
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set the playback drawables
        updatePlaybackControls();
        // Current info
        updateNowPlayingInfo();
        // Refresh the queue
        ((QueueFragment) mPagerAdapter.getFragment(0)).refreshQueue();
        initRemoveAds();
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
        // Refresh the current time
        final long next = refreshCurrentTime();
        queueNextRefresh(next);
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
        mImageFetcher.flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIsPaused = false;

        try {
            if (mMopubAd != null) {
                mMopubAd.destroy();
            }
        } catch (Throwable ignored) {
            LOG.error(ignored.getMessage(), ignored);
        }

        try {
            mTimeHandler.removeMessages(REFRESH_TIME);
        } catch (Throwable ignored) {
            LOG.error(ignored.getMessage(), ignored);
        }

        if (musicPlaybackService != null) {
            MusicUtils.unbindFromService(mToken);
            mToken = null;
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
        mPageContainer.setBackgroundResource(R.drawable.audio_player_pager_container);

        // Now playing header
        mAudioPlayerHeader = findView(R.id.audio_player_header);
        // Opens the currently playing album profile
        mAudioPlayerHeader.setOnClickListener(mOpenAlbumProfile);

        // Used to hide the artwork and show the queue
        final FrameLayout mSwitch = findView(R.id.audio_player_switch);
        mSwitch.setOnClickListener(mToggleHiddenPanel);

        // Initialize the pager adapter
        mPagerAdapter = new PagerAdapter(this);
        // Queue
        mPagerAdapter.add(QueueFragment.class, null);

        // Initialize the ViewPager
        ViewPager mViewPager = findView(R.id.audio_player_pager);
        // Attach the adapter
        mViewPager.setAdapter(mPagerAdapter);
        // Offscreen pager loading limit
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount() - 1);
        // Play and pause button
        mPlayPauseButton = findView(R.id.action_button_play);
        // Shuffle button
        mShuffleButton = findView(R.id.action_button_shuffle);
        // Repeat button
        mRepeatButton = findView(R.id.action_button_repeat);
        // Previous button
        RepeatingImageButton mPreviousButton = findView(R.id.action_button_previous);
        // Next button
        RepeatingImageButton mNextButton = findView(R.id.action_button_next);
        // Track name
        mTrackName = findView(R.id.audio_player_track_name);
        // Artist name
        mArtistName = findView(R.id.audio_player_artist_name);
        // Album art
        mAlbumArt = findView(R.id.audio_player_album_art);
        // Small album art
        mAlbumArtSmall = findView(R.id.audio_player_switch_album_art);
        // Current time
        mCurrentTime = findView(R.id.audio_player_current_time);
        // Total time
        mTotalTime = findView(R.id.audio_player_total_time);
        // Used to show and hide the queue fragment
        mQueueSwitch = findView(R.id.audio_player_switch_queue);
        // Theme the queue switch icon
        mQueueSwitch.setImageResource(R.drawable.btn_switch_queue);
        // Progress
        mProgress = findView(android.R.id.progress);

        // Set the repeat listener for the previous button
        mPreviousButton.setRepeatListener(mRewindListener);
        // Set the repeat listener for the next button
        mNextButton.setRepeatListener(mFastForwardListener);
        // Update the progress
        mProgress.setOnSeekBarChangeListener(this);
    }

    private void initAlbumArtBanner() {
        long timeSinceLastBannerInit = System.currentTimeMillis() - lastInitAlbumArtBanner;

        if (timeSinceLastBannerInit < 5000) {
            LOG.info("initAlbumArtBanner() aborted, too soon to attempt another banner load");
            return;
        }

        if (Offers.disabledAds()) {
            return;
        }

        if ((mMopubAd != null && mMopubAd.getVisibility() == View.VISIBLE) ||
            (mFallbackAd != null && mFallbackAd.getVisibility() == View.VISIBLE)) {
            // ad is already visible, leave as is
            return;
        }

        if (!UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_MOPUB_ALBUM_ART_BANNER_THRESHOLD)) {
            return;
        }

        lastInitAlbumArtBanner = System.currentTimeMillis();

        StringBuilder keywords = new StringBuilder("music,audio,ringtone");
        String artistName = MusicUtils.getArtistName();
        String albumName = MusicUtils.getAlbumName();
        if (artistName != null) {
            keywords.append(",").append(artistName);
        }
        if (albumName != null) {
            keywords.append(",").append(albumName);
        }

        if (mMopubAd != null && mDismissAlbumArtAdButton != null) {
            mDismissAlbumArtAdButton.setOnClickListener(onDismissAlbumArtAdButtonClickListener);

            loadFallbackBanner();

            //mMopubAd.setTesting(true);
            mMopubAd.setAutorefreshEnabled(true);
            mMopubAd.setAdUnitId("c737d8a55b2e41189aa1532ae0520ad1");
            mMopubAd.setKeywords(keywords.toString());
            mMopubAd.setBannerAdListener(moPubBannerListener);

            try {
                mMopubAd.loadAd();
            } catch (Throwable e) {
                LOG.warn("AudioPlayer Mopub banner could not be loaded", e);
                loadFallbackBanner();
            }
        }
    }

    private void loadFallbackBanner() {
        LOG.info("loadFallbackBanner");
        InHouseBannerFactory.loadAd(mFallbackAd, InHouseBannerFactory.AdFormat.BIG_300x250);
        setBannerViewVisibility(mMopubAd, false);
        setBannerViewVisibility(mFallbackAd, true);
    }

    private void setBannerViewVisibility(View bannerView, boolean visible) {
        if (bannerView != null && mDismissAlbumArtAdButton != null) {
            int adVisibility = visible ? View.VISIBLE : View.GONE;
            int albumArtVisibility = visible ? View.GONE : View.VISIBLE;
            bannerView.setVisibility(adVisibility);
            if (adVisibility == View.VISIBLE) {
                bannerView.requestLayout();
            }
            mDismissAlbumArtAdButton.setVisibility(adVisibility);
            mAdvertisementText.setVisibility(adVisibility);
            mAlbumArt.setVisibility(albumArtVisibility);

            TextView removeAdsTextView = findView(R.id.audio_player_remove_ads_text_link);
            if (removeAdsTextView != null) {
                removeAdsTextView.setVisibility(Offers.removeAdsOffersEnabled() && visible ?
                        View.VISIBLE : View.GONE);
            }
        }
    }


    /**
     * Sets the track name, album name, and album art.
     */
    private void updateNowPlayingInfo() {
        // Set the track name
        mTrackName.setText(MusicUtils.getTrackName());
        // Set the artist name
        mArtistName.setText(getArtistAndAlbumName());//MusicUtils.getArtistName());
        // Set the total time
        mTotalTime.setText(MusicUtils.makeTimeString(this, MusicUtils.duration() / 1000));
        // Set the album art
        mImageFetcher.loadCurrentArtwork(mAlbumArt);
        // Set the small artwork
        mImageFetcher.loadCurrentArtwork(mAlbumArtSmall);
        // Update the current time
        queueNextRefresh(1);

    }

    private String getArtistAndAlbumName() {
        String str = "";

        String artist = MusicUtils.getArtistName();
        String album = MusicUtils.getAlbumName();

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

        if (intent == null || musicPlaybackService == null) {
            return;
        }

        Uri uri = intent.getData();
        String mimeType = intent.getType();
        boolean handled = false;

        if (uri != null && uri.toString().length() > 0) {
            MusicUtils.playFile(uri);
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
        // Set the play and pause image
        mPlayPauseButton.updateState();
        // Set the shuffle image
        mShuffleButton.updateShuffleState();
        // Set the repeat image
        mRepeatButton.updateRepeatState();
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
        if (musicPlaybackService == null) {
            return;
        }
        if (repcount == 0) {
            mStartSeekPos = MusicUtils.position();
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
            if (newpos < 0) {
                // move to previous track
                MusicUtils.previous(this);
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
            refreshCurrentTime();
        }
    }

    /**
     * Used to scan forwards in time through the current track
     *
     * @param repcount The repeat count
     * @param delta    The long press duration
     */
    private void scanForward(final int repcount, long delta) {
        if (musicPlaybackService == null) {
            return;
        }
        if (repcount == 0) {
            mStartSeekPos = MusicUtils.position();
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
            final long duration = MusicUtils.duration();
            if (newpos >= duration) {
                // move to next track
                MusicUtils.next();
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
            refreshCurrentTime();
        }
    }

    private void refreshCurrentTimeText(final long pos) {
        mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
    }

    /* Used to update the current time string */
    private long refreshCurrentTime() {
        if (musicPlaybackService == null) {
            return 500;
        }
        try {
            final long pos = mPosOverride < 0 ? MusicUtils.position() : mPosOverride;
            if (pos >= 0 && MusicUtils.duration() > 0) {
                refreshCurrentTimeText(pos);
                final int progress = (int) (1000 * pos / MusicUtils.duration());
                mProgress.setProgress(progress);

                if (mFromTouch) {
                    return 500;
                } else if (MusicUtils.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    final int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE
                            : View.INVISIBLE);
                    return 500;
                }
            } else {
                mCurrentTime.setText("--:--");
                mProgress.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second,
            // so
            // the counter can be updated at just the right time
            final long remaining = 1000 - pos % 1000;
            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mProgress.getWidth();
            if (width == 0) {
                width = 320;
            }
            final long smoothrefreshtime = MusicUtils.duration() / width;
            if (smoothrefreshtime > remaining) {
                return remaining;
            }
            if (smoothrefreshtime < 20) {
                return 20;
            }
            return smoothrefreshtime;
        } catch (final Exception ignored) {

        }
        return 500;
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
        mPageContainer.setVisibility(View.INVISIBLE);
        mAlbumArtSmall.setVisibility(View.GONE);
        mQueueSwitch.setVisibility(View.VISIBLE);
        // Fade out the pager container
        fade(mPageContainer, 0f);
        // Fade in the album art
        fade(mAlbumArt, 1f);
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
        final long currentAudioId = MusicUtils.getCurrentAudioId();
        final String trackName = MusicUtils.getTrackName();
        if (currentAudioId == -1 || trackName == null) {
            return;
        }
        final Intent shareIntent = new Intent();
        final String artistName = MusicUtils.getArtistName();

        final String shareMessage = (artistName != null) ? getString(R.string.now_listening_to, trackName, artistName) :
                getString(R.string.now_listening_to_no_artist_available, trackName);

        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + currentAudioId));
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_track_using)));
    }

    private void toggleFavorite() {
        MusicUtils.toggleFavorite();
        invalidateOptionsMenu();
    }

    /**
     * Used to scan backwards through the track
     */
    private final RepeatingImageButton.RepeatListener mRewindListener = new RepeatingImageButton.RepeatListener() {
        @Override
        public void onRepeat(final View v, final long howlong, final int repcnt) {
            scanBackward(repcnt, howlong);
        }
    };

    /**
     * Used to scan ahead through the track
     */
    private final RepeatingImageButton.RepeatListener mFastForwardListener = new RepeatingImageButton.RepeatListener() {
        @Override
        public void onRepeat(final View v, final long howlong, final int repcnt) {
            scanForward(repcnt, howlong);
        }
    };

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
    private final OnClickListener mOpenAlbumProfile = new OnClickListener() {
        @Override
        public void onClick(View v) {
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
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case REFRESH_TIME:
                    final long next = mAudioPlayer.get().refreshCurrentTime();
                    mAudioPlayer.get().queueNextRefresh(next);
                    break;
                default:
                    break;
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
            final String action = intent.getAction();

            switch (action) {
                case MusicPlaybackService.META_CHANGED:
                    // Current info
                    activity.updateNowPlayingInfo();
                    // Update the favorites icon
                    activity.invalidateOptionsMenu();
                    activity.updateQueueFragmentCurrentSong();
                    activity.initAlbumArtBanner();
                    break;
                case MusicPlaybackService.PLAYSTATE_CHANGED:
                    // Set the play and pause image
                    activity.mPlayPauseButton.updateState();
                    activity.initAlbumArtBanner();
                    break;
                case MusicPlaybackService.REPEATMODE_CHANGED:
                case MusicPlaybackService.SHUFFLEMODE_CHANGED:
                    // Set the repeat image
                    activity.mRepeatButton.updateRepeatState();
                    // Set the shuffle image
                    activity.mShuffleButton.updateShuffleState();
                    activity.initAlbumArtBanner();
                    break;
            }
        }
    }

    private final class PlayerGestureListener extends SwipeLayout.SwipeGestureAdapter
            implements View.OnTouchListener {

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
                MusicUtils.musicPlaybackService.next();
                UXStats.instance().log(UXAction.PLAYER_GESTURE_SWIPE_SONG);
            } catch (Throwable e) {
                // ignore
            }
        }

        @Override
        public void onSwipeRight() {
            try {
                MusicUtils.musicPlaybackService.prev();
                UXStats.instance().log(UXAction.PLAYER_GESTURE_SWIPE_SONG);
            } catch (Throwable e) {
                // ignore
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            try {
                toggleFavorite();
                UXStats.instance().log(UXAction.PLAYER_TOGGLE_FAVORITE);
            } catch (Throwable t) {
                return false;
            }
            return true;
        }

        private void onMultiTouchEvent() {
            try {
                MusicUtils.playOrPause();
                UXStats.instance().log(UXAction.PLAYER_GESTURE_PAUSE_RESUME);
            } catch (Throwable e) {
                // ignore
            }
        }
    }
}
