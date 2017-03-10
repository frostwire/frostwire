/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.ImageLoader;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class MiniPlayerView extends LinearLayout {

    private TextView titleText;
    private TextView artistText;
    private ImageView coverImage;
    private TimerObserver refresher;

    public MiniPlayerView(Context context, AttributeSet set) {
        super(context, set);
        refresher = new TimerObserver() {
            @Override
            public void onTime() {
                refresherOnTime();
            }
        };
    }

    public TimerObserver getRefresher() {
        return refresher;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_miniplayer, this);
        if (isInEditMode()) {
            return;
        }
        titleText = (TextView) findViewById(R.id.view_miniplayer_title);
        artistText = (TextView) findViewById(R.id.view_miniplayer_artist);
        coverImage = (ImageView) findViewById(R.id.view_miniplayer_cover);
        coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        artistText.setEllipsize(TextUtils.TruncateAt.END);
        initEventHandlers();
        refreshComponents();
    }

    private void initEventHandlers() {
        OnClickListener goToAudioPlayerActivityListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                openAudioPlayerActivity();
            }
        };
        coverImage.setOnClickListener(goToAudioPlayerActivityListener);

        LinearLayout statusContainer = (LinearLayout) findViewById(R.id.view_miniplayer_status_container);
        statusContainer.setOnClickListener(goToAudioPlayerActivityListener);

        ImageView previous = (ImageView) findViewById(R.id.view_miniplayer_previous);
        previous.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onPreviousClick();
            }
        });

        ImageView playPause = (ImageView) findViewById(R.id.view_miniplayer_play_pause);
        playPause.setClickable(true);
        playPause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayPauseClick();
            }
        });
        playPause.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onPlayPauseLongClick();
                return true;
            }
        });
        ImageView next = (ImageView) findViewById(R.id.view_miniplayer_next);
        next.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onNextClick();
            }
        });
    }

    private void onPreviousClick() {
        MusicUtils.previous(getContext());
        refreshComponents();
    }

    private void onNextClick() {
        MusicUtils.next();
        refreshComponents();
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
        MusicUtils.playOrPause();
        refreshComponents();
    }

    private void refreshComponents() {
        refreshPlayPauseIcon();
        refreshAlbumCover();
    }

    private void refreshAlbumCover() {
        long currentAlbumId = MusicUtils.getCurrentAlbumId();
        if (currentAlbumId != -1) {
            Uri albumUri = ImageLoader.getAlbumArtUri(currentAlbumId);
            ImageLoader.getInstance(getContext()).load(albumUri, coverImage);
        } else {
            coverImage.setBackgroundResource(R.drawable.default_artwork);
        }
    }

    private void refreshPlayPauseIcon() {
        ImageView playPauseButton = (ImageView) findViewById(R.id.view_miniplayer_play_pause);
        int notifierResourceId;
        if (!MusicUtils.isPlaying()) {
            notifierResourceId = R.drawable.btn_playback_play_bottom;
        } else {
            notifierResourceId = R.drawable.btn_playback_pause_bottom;
        }
        playPauseButton.setBackgroundResource(notifierResourceId);
    }

    public void refresherOnTime() {
        CoreMediaPlayer mp = Engine.instance().getMediaPlayer();
        if (mp != null) {
            FileDescriptor fd = mp.getCurrentFD();

            String title = "";
            String artist = "";
            refreshComponents();
            if (fd != null) {
                title = fd.title;
                artist = fd.artist;
                if (getVisibility() == View.GONE) {
                    setVisibility(View.VISIBLE);
                }
            } else {
                if (getVisibility() == View.VISIBLE) {
                    setVisibility(View.GONE);
                }
            }
            titleText.setText(title);
            artistText.setText(artist);
        }
    }

    private void openAudioPlayerActivity() {
        Intent i = new Intent(getContext(), AudioPlayerActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
    }
}
