/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.offers.FWBannerView;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.search.FileSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PreviewPlayerActivity extends AbstractActivity implements
        AbstractDialog.OnDialogClickListener,
        TextureView.SurfaceTextureListener,
        MediaPlayer.OnBufferingUpdateListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnVideoSizeChangedListener,
        MediaPlayer.OnInfoListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final Logger LOG = Logger.getLogger(PreviewPlayerActivity.class);
    public static WeakReference<FileSearchResult> srRef;
    private static String definitiveStreamUrl;

    private MediaPlayer androidMediaPlayer;
    private Surface surface;
    private String displayName;
    private String source;
    private String thumbnailUrl;
    private String streamUrl;
    private boolean hasVideo;
    private boolean audio;
    private boolean isFullScreen = false;
    private boolean videoSizeSetupDone = false;
    private boolean changedActionBarTitleToNonBuffering = false;
    private FWBannerView fwBannerView;
    private boolean isVertical;

    public PreviewPlayerActivity() {
        super(R.layout.activity_preview_player);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_preview_player_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem fullscreenMenu = menu.findItem(R.id.activity_preview_player_menu_fullscreen);
        if (fullscreenMenu != null) {
            fullscreenMenu.setVisible(!audio);  //userRegistered is boolean, pointing if the user has registered or not.
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.activity_preview_player_menu_fullscreen) {
            final TextureView videoTexture = findView(R.id.activity_preview_player_videoview);
            toggleFullScreen(videoTexture);

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void initComponents(Bundle savedInstanceState) {
        Intent i = getIntent();
        if (i == null) {
            finish();
            return;
        }

        stopAnyOtherPlayers();

        displayName = i.getStringExtra("displayName");
        source = i.getStringExtra("source");
        thumbnailUrl = i.getStringExtra("thumbnailUrl");
        streamUrl = i.getStringExtra("streamUrl");
        hasVideo = i.getBooleanExtra("hasVideo", false);
        audio = i.getBooleanExtra("audio", false);
        isFullScreen = i.getBooleanExtra("isFullScreen", false);

        int mediaTypeStrId = audio ? R.string.audio : R.string.video;
        setTitle(getString(R.string.media_preview, getString(mediaTypeStrId)) + " (buffering...)");

        final TextureView videoTexture = findView(R.id.activity_preview_player_videoview);
        videoTexture.setSurfaceTextureListener(this);

        // when previewing audio, we make the video view really tiny.
        // hiding it will cause the player not to play.
        if (audio) {
            final ViewGroup.LayoutParams layoutParams = videoTexture.getLayoutParams();
            layoutParams.width = 1;
            layoutParams.height = 1;
            videoTexture.setLayoutParams(layoutParams);
        }

        final ImageView img = findView(R.id.activity_preview_player_thumbnail);

        final TextView trackName = findView(R.id.activity_preview_player_track_name);
        final TextView artistName = findView(R.id.activity_preview_player_artist_name);
        trackName.setText(displayName);
        artistName.setText(source);

        if (!audio) {
            videoTexture.setOnTouchListener((view, motionEvent) -> {
                toggleFullScreen(videoTexture);
                return false;
            });
        }

        if (thumbnailUrl != null) {
            FWImageLoader.getInstance(this).load(Uri.parse(thumbnailUrl), img, R.drawable.default_artwork);
        }

        final Button downloadButton = findView(R.id.activity_preview_player_download_button);
        downloadButton.setOnClickListener(v -> onDownloadButtonClick());

        if (isFullScreen) {
            isFullScreen = false; //so it will make it full screen on what was an orientation change.
            toggleFullScreen(videoTexture);
        }

        initFWBannerView();
    }

    private void initFWBannerView() {
        if (Offers.disabledAds()) {
            hideAd();
            return;
        }
        if (fwBannerView == null) {
            fwBannerView = findViewById(R.id.activity_preview_player_320x50_banner);
        }
        if (fwBannerView != null) {
            fwBannerView.setOnBannerDismissedListener(this::hideAd);
            fwBannerView.loadMaxBanner();
            fwBannerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (outState != null) {
            super.onSaveInstanceState(outState);
            outState.putString("displayName", displayName);
            outState.putString("source", source);
            outState.putString("thumbnailUrl", thumbnailUrl);
            outState.putString("streamUrl", streamUrl);
            outState.putBoolean("hasVideo", hasVideo);
            outState.putBoolean("audio", audio);
            outState.putBoolean("isFullScreen", isFullScreen);
            if (androidMediaPlayer != null && androidMediaPlayer.isPlaying()) {
                outState.putInt("currentPosition", androidMediaPlayer.getCurrentPosition());
            }
        }
    }

    private void onVideoViewPrepared(final ImageView img) {
        final Button downloadButton = findView(R.id.activity_preview_player_download_button);
        downloadButton.setVisibility(!isFullScreen ? View.VISIBLE : View.GONE);
        if (!audio) {
            img.setVisibility(View.GONE);
        }
    }

    private void onDownloadButtonClick() {
        if (Ref.alive(srRef)) {
            final FileSearchResult fileSearchResult = srRef.get();
            NewTransferDialog dlg = NewTransferDialog.newInstance(fileSearchResult, false, this);
            dlg.show(getSupportFragmentManager(), AbstractDialog.getSuggestedTAG(NewTransferDialog.class) );
        } else {
            finish();
        }
    }

    /**
     * Tries to get an alternate URI on a HTTP Location header,
     * if the header isn't present it returns the url parameter
     * The result is cached in the definitiveStreamUrl member variable.
     */
    private static String getDefinitiveStreamUrl(String url) {
        if (definitiveStreamUrl != null) {
            return definitiveStreamUrl;
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) (new URL(url).openConnection());
            con.setInstanceFollowRedirects(false);
            con.connect();
            String location = con.getHeaderField("Location");

            if (location != null) {
                definitiveStreamUrl = location;
                return definitiveStreamUrl;
            }
            definitiveStreamUrl = url;
        } catch (Throwable e) {
            LOG.error("Unable to detect final url", e);
        } finally {
            if (con != null) {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                    // ignore
                }
            }
        }
        return definitiveStreamUrl;
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(NewTransferDialog.TAG) && which == Dialog.BUTTON_POSITIVE) {
            if (Ref.alive(NewTransferDialog.srRef)) {
                definitiveStreamUrl = null;
                releaseMediaPlayer();
                Intent i = new Intent(this, MainActivity.class);
                i.setAction(Constants.ACTION_START_TRANSFER_FROM_PREVIEW);
                i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(i);
            }
            finish();
        }
    }


    private void toggleFullScreen(TextureView v) {
        videoSizeSetupDone = false;
        DisplayMetrics metrics = new DisplayMetrics();
        final Display defaultDisplay = getWindowManager().getDefaultDisplay();
        defaultDisplay.getMetrics(metrics);

        final FrameLayout frameLayout = findView(R.id.activity_preview_player_framelayout);
        LinearLayout.LayoutParams frameLayoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();

        LinearLayout playerMetadataHeader = findView(R.id.activity_preview_player_metadata_header);
        ImageView thumbnail = findView(R.id.activity_preview_player_thumbnail);

        final Button downloadButton = findView(R.id.activity_preview_player_download_button);

        if (fwBannerView == null) {
            fwBannerView = findView(R.id.activity_preview_player_320x50_banner);
        }

        // Let's Go into full screen mode.
        if (!isFullScreen) {
            //hides the status bar
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

            findToolbar().setVisibility(View.GONE);
            setViewsVisibility(View.GONE, playerMetadataHeader, thumbnail, downloadButton);

            fwBannerView.setLayersVisibility(FWBannerView.Layers.ALL, false);

            //noinspection SuspiciousNameCombination
            frameLayoutParams.width = metrics.heightPixels;
            //noinspection SuspiciousNameCombination
            frameLayoutParams.height = metrics.widthPixels;

            isFullScreen = true;
        } else {
            // restore components back from full screen mode.
            //final TextureView videoTexture = findView(R.id.activity_preview_player_videoview);

            //restores the status bar to view
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            findToolbar().setVisibility(View.VISIBLE);
            setViewsVisibility(View.VISIBLE, playerMetadataHeader, downloadButton);
            if (Offers.disabledAds()) {
                hideAd();
            } else {
                if (fwBannerView != null) {
                    fwBannerView.setVisibility(View.VISIBLE);
                }
            }
            v.setRotation(0);

            // restore the thumbnail on the way back only if doing audio preview.
            //thumbnail.setVisibility(!audio ? View.GONE : View.VISIBLE);
            frameLayoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            frameLayoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            frameLayoutParams.weight = 1.0f;

            isFullScreen = false;
        }

        frameLayout.setLayoutParams(frameLayoutParams);
        changeVideoSize();
    }

    private void changeVideoSize() {
        if (androidMediaPlayer == null) {
            return;
        }
        int videoWidth = androidMediaPlayer.getVideoWidth();
        int videoHeight = androidMediaPlayer.getVideoHeight();
        final TextureView v = findView(R.id.activity_preview_player_videoview);
        DisplayMetrics metrics = new DisplayMetrics();
        final Display defaultDisplay = getWindowManager().getDefaultDisplay();
        defaultDisplay.getMetrics(metrics);

        final android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) v.getLayoutParams();

        float hRatio = (videoHeight * 1.0f) / (videoWidth * 1.0f);
        float rotation = 0;

        if (isFullScreen) {
            //noinspection SuspiciousNameCombination
            params.width = metrics.heightPixels;
            //noinspection SuspiciousNameCombination
            params.height = metrics.widthPixels;
            params.gravity = Gravity.TOP;
            v.setPivotY((float) metrics.widthPixels / 2.0f);
            rotation = 90f;
        } else {
            params.width = metrics.widthPixels;
            params.height = (int) (params.width * hRatio);
            params.gravity = Gravity.CENTER;
        }

        v.setRotation(rotation);
        v.setLayoutParams(params);
        videoSizeSetupDone = true;
    }

    /**
     * Utility method: change the visibility for a bunch of views. Skips null views.
     */
    private void setViewsVisibility(int visibility, View... views) {
        if (visibility != View.INVISIBLE && visibility != View.VISIBLE && visibility != View.GONE) {
            throw new IllegalArgumentException("Invalid visibility constant");
        }
        if (views == null) {
            throw new IllegalArgumentException("Views argument can't be null");
        }
        for (View v : views) {
            if (v != null) {
                v.setVisibility(visibility);
            }
        }
    }

    private void releaseMediaPlayer() {
        if (androidMediaPlayer != null) {
            androidMediaPlayer.stop();
            androidMediaPlayer.setSurface(null);
            try {
                androidMediaPlayer.release();
            } catch (Throwable t) {
                //there could be a runtime exception thrown inside stayAwake()
            }
            androidMediaPlayer = null;

            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.abandonAudioFocus(this);
            }
        }
    }

    private static void onConnectionDroppedError(final WeakReference<PreviewPlayerActivity> contextRef) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (Ref.alive(contextRef)) {
                contextRef.get().finish();
                UIUtils.showLongMessage(contextRef.get(), R.string.check_internet_connection);
            }
        });
    }

    private static void launchPlayerWithFinalStreamUrl(final WeakReference<PreviewPlayerActivity> contextRef, final Uri uri) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!Ref.alive(contextRef)) {
                return;
            }
            contextRef.get().androidMediaPlayer = new MediaPlayer();
            Surface surface = (!contextRef.get().audio ? contextRef.get().surface : null);
            MediaPlayer androidMediaPlayer = contextRef.get().androidMediaPlayer;
            try {
                androidMediaPlayer.setDataSource(contextRef.get(), uri);
                androidMediaPlayer.setSurface(surface);
                androidMediaPlayer.setOnBufferingUpdateListener(contextRef.get());
                androidMediaPlayer.setOnCompletionListener(contextRef.get());
                androidMediaPlayer.setOnPreparedListener(contextRef.get());
                androidMediaPlayer.setOnVideoSizeChangedListener(contextRef.get());
                androidMediaPlayer.setOnInfoListener(contextRef.get());
                androidMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                androidMediaPlayer.prepare();
                androidMediaPlayer.start();
                if (MusicUtils.isPlaying()) {
                    MusicUtils.playPauseOrResume();
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        final WeakReference<PreviewPlayerActivity> contextRef = Ref.weak(this);
        final String streamUrlCopy = streamUrl;
        Engine.instance().getThreadPool().execute(() -> {
            String finalUrl = PreviewPlayerActivity.getDefinitiveStreamUrl(streamUrlCopy);
            if (finalUrl == null) {
                PreviewPlayerActivity.onConnectionDroppedError(contextRef);
                return;
            }
            final Uri uri = Uri.parse(finalUrl);
            PreviewPlayerActivity.launchPlayerWithFinalStreamUrl(contextRef, uri);
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        if (androidMediaPlayer != null) {
            if (surface != null) {
                surface.release();
                surface = new Surface(surfaceTexture);
            }
            androidMediaPlayer.setSurface(!audio ? surface : null);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (androidMediaPlayer != null) {
            androidMediaPlayer.setSurface(null);
            this.surface.release();
            this.surface = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        finish();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(PreviewPlayerActivity.this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }

        final ImageView img = findView(R.id.activity_preview_player_thumbnail);
        onVideoViewPrepared(img);
        if (mp != null) {
            changeVideoSize();
        }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (width > 0 && height > 0 && !videoSizeSetupDone) {
            changeVideoSize();
        }
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        boolean startedPlayback = false;
        switch (what) {
            case MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                //LOG.warn("Media is too complex to decode it fast enough.");
                //startedPlayback = true;
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                //LOG.warn("Start of media buffering.");
                //startedPlayback = true;
                break;
            case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                //LOG.warn("End of media buffering.");
                changeVideoSize();
                startedPlayback = true;
                break;
            case MediaPlayer.MEDIA_INFO_UNKNOWN:
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
            case MediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
            case MediaPlayer.MEDIA_INFO_METADATA_UPDATE:
            default:
                break;
        }

        if (startedPlayback && !changedActionBarTitleToNonBuffering) {
            int mediaTypeStrId = audio ? R.string.audio : R.string.video;
            setTitle(getString(R.string.media_preview, getString(mediaTypeStrId)));
            changedActionBarTitleToNonBuffering = true;
        }

        return false;
    }

    public void stopAnyOtherPlayers() {
        try {
            final CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
        } catch (Throwable ignored) {
        }

        AudioManager mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (mAudioManager != null && mAudioManager.isMusicActive()) {
            Intent i = new Intent("com.android.music.musicservicecommand");
            i.putExtra("command", "pause");
            getApplication().sendBroadcast(i);
        }
    }

    @Override
    protected void onDestroy() {
        definitiveStreamUrl = null;
        destroyMopubView();
        stopAnyOtherPlayers();
        releaseMediaPlayer();
        super.onDestroy();
    }


    @Override
    protected void onPause() {
        destroyMopubView();
        releaseMediaPlayer();
        super.onPause();
    }

    private void destroyMopubView() {
        try {
            hideAd();
            if (fwBannerView != null) {
                fwBannerView.destroy();
            }
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    private void hideAd() {
        if (fwBannerView != null) {
            fwBannerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        changedActionBarTitleToNonBuffering = false;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            definitiveStreamUrl = null;
            releaseMediaPlayer();
            int mediaTypeStrId = audio ? R.string.audio : R.string.video;
            setTitle(getString(R.string.media_preview, getString(mediaTypeStrId)));
        }
    }
}
