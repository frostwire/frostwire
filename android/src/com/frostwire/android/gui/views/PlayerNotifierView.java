/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.services.Engine;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
public class PlayerNotifierView extends LinearLayout {

    private TextView titleText;
    private TextView artistText;
    private ImageView coverImage;
    private TimerObserver refresher;

    public PlayerNotifierView(Context context, AttributeSet set) {
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

        View.inflate(getContext(), R.layout.view_player_notifier, this);

        if (isInEditMode()) {
            return;
        }

        titleText = (TextView) findViewById(R.id.view_player_notifier_title);
        artistText = (TextView) findViewById(R.id.view_player_notifier_artist);
        coverImage = (ImageView) findViewById(R.id.view_player_notifier_cover);
        coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        refreshPlayerStateIndicator();
    }

    private void refreshPlayerStateIndicator() {
        CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }
        ImageView notifierIconImageView = (ImageView) findViewById(R.id.view_player_notifier_play_pause);
        int notifierResourceId;
        if (!mediaPlayer.isPlaying()) {
            notifierResourceId = R.drawable.btn_playback_play_bottom;
        } else {
            notifierResourceId = R.drawable.btn_playback_pause_bottom;
        }
        notifierIconImageView.setBackgroundResource(notifierResourceId);
    }

    public void refresherOnTime() {
        CoreMediaPlayer mp = Engine.instance().getMediaPlayer();
        if (mp != null) {
            FileDescriptor fd = mp.getCurrentFD();

            String title = "";
            String artist = "";
            refreshPlayerStateIndicator();
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Engine.instance().getMediaPlayer().getCurrentFD() != null) {
            Intent i = new Intent(getContext(), AudioPlayerActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        }
        return true;
    }
}
