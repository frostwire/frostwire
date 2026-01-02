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

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.TaskThrottle;

/**
 * See also Apollo's BaseActivity and StopAndHideBottomActionBarListener if you're trying
 * to update the mini player in My Music.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class MiniPlayerView extends LinearLayout {

    public static final int REFRESHER_INTERVAL_IN_SECS = 1; // See MainActivity.initComponents(), that's where the subscription to the timer service happens
    private TextView titleText;
    private TextView artistText;
    private ImageView coverImage;
    private ImageView playPauseButton;
    private boolean isPlaying = false;
    private long currentAlbumId;
    private final TimerObserver refresher;

    public MiniPlayerView(Context context, AttributeSet set) {
        super(context, set);
        refresher = this::refresherOnTime;
    }

    public TimerObserver getRefresher() {
        return refresher;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_miniplayer, this);

        titleText = findViewById(R.id.view_miniplayer_title);
        artistText = findViewById(R.id.view_miniplayer_artist);
        coverImage = findViewById(R.id.view_miniplayer_cover);

        if (isInEditMode()) {
            return; // skip component logic
        }

        initEventHandlers();
        refreshComponents();
        refreshAlbumCover();
    }

    private void initEventHandlers() {
        OnClickListener goToAudioPlayerActivityListener = v -> NavUtils.openAudioPlayer((Activity) getContext());
        coverImage.setOnClickListener(goToAudioPlayerActivityListener);

        LinearLayout statusContainer = findViewById(R.id.view_miniplayer_status_container);
        statusContainer.setOnClickListener(goToAudioPlayerActivityListener);

        ImageView previous = findViewById(R.id.view_miniplayer_previous);
        previous.setOnClickListener(v -> onPreviousClick());

        playPauseButton = findViewById(R.id.view_miniplayer_play_pause);
        playPauseButton.setClickable(true);
        playPauseButton.setOnClickListener(v -> onPlayPauseClick());
        playPauseButton.setOnLongClickListener(v -> {
            onPlayPauseLongClick();
            return true;
        });
        ImageView next = findViewById(R.id.view_miniplayer_next);
        next.setOnClickListener(v -> onNextClick());
    }

    private void onPreviousClick() {
        MusicUtils.previous();
        refreshComponents();
        refreshAlbumCover();
    }

    private void onNextClick() {
        MusicUtils.next();
        refreshComponents();
        refreshAlbumCover();
    }

    private void onPlayPauseLongClick() {
        CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }
        mediaPlayer.stop();
        setVisibility(View.GONE);
    }

    private void onPlayPauseClick() {
        CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }
        MusicUtils.playPauseOrResume();
        refreshComponents();
        refreshAlbumCover();
    }

    private void refreshComponents() {
        refreshPlayPauseIcon();
    }

    private void refreshAlbumCover() {
        if (!TaskThrottle.isReadyToSubmitTask("MiniPlayerView::refreshAlbumCover", 5000)) {
            return;
        }

        if (currentAlbumId != -1) {
            Uri albumUri = FWImageLoader.getAlbumArtUri(currentAlbumId);
            FWImageLoader.getInstance(getContext()).load(albumUri, coverImage);
        } else if (coverImage != null) {
            coverImage.setBackgroundResource(R.drawable.default_artwork);
        }
    }

    private void refreshPlayPauseIcon() {
        int notifierResourceId;
        if (!isPlaying) {
            notifierResourceId = R.drawable.btn_playback_play_bottom;
        } else {
            notifierResourceId = R.drawable.btn_playback_pause_bottom;
        }
        playPauseButton.setBackgroundResource(notifierResourceId);
    }

    public void refresherOnTime() {
        if (!MusicUtils.isMusicPlaybackServiceRunning()) {
            // rest :)
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            FWFileDescriptor fd = refreshOnTimerResultTask(this);
            SystemUtils.postToUIThread(() -> refreshOnTimerPostTask(this, fd));
        });
    }

    private static FWFileDescriptor refreshOnTimerResultTask(MiniPlayerView miniPlayer) {
        CoreMediaPlayer mp = Engine.instance().getMediaPlayer();
        if (mp != null) {
            miniPlayer.isPlaying = MusicUtils.isPlaying();
            miniPlayer.currentAlbumId = MusicUtils.getCurrentAlbumId();
            return mp.getCurrentFD(miniPlayer.getContext());
        }
        return null;
    }

    private static void refreshOnTimerPostTask(MiniPlayerView miniPlayer, FWFileDescriptor fd) {
        String title = "";
        String artist = "";
        if (fd != null) {
            title = fd.title;
            artist = fd.artist;
            if (miniPlayer != null) {
                if (miniPlayer.getVisibility() == View.GONE) {
                    miniPlayer.setVisibility(View.VISIBLE);
                }
                miniPlayer.refreshComponents();
            }
        } else {
            if (miniPlayer != null && miniPlayer.getVisibility() == View.VISIBLE) {
                miniPlayer.setVisibility(View.GONE);
            }
        }
        if (miniPlayer != null) {
            miniPlayer.titleText.setText(title);
            miniPlayer.artistText.setText(artist);
        }
    }

    private void openAudioPlayerActivity() {
        Intent i = new Intent(getContext(), AudioPlayerActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }
}
