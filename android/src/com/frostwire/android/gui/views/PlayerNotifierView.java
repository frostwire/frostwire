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
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
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
public class PlayerNotifierView extends LinearLayout implements TimerObserver {

    private String lastStatusShown;
    private TextView statusText;
    private LinearLayout statusContainer;

    private TranslateAnimation fromRightAnimation;

    private TranslateAnimation showNotifierAnimation;
    private TranslateAnimation hideNotifierAnimation;

    public PlayerNotifierView(Context context, AttributeSet set) {
        super(context, set);

        initAnimations();
    }

    private void initAnimations() {
        fromRightAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        fromRightAnimation.setDuration(500);
        fromRightAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        showNotifierAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        showNotifierAnimation.setDuration(300);
        showNotifierAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        hideNotifierAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
        hideNotifierAnimation.setDuration(300);
        hideNotifierAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        
        View.inflate(getContext(), R.layout.view_player_notifier, this);
        
        if (isInEditMode()) {
            return;
        }

        statusText = (TextView) findViewById(R.id.view_player_notifier_status);
        statusContainer = (LinearLayout) findViewById(R.id.view_player_notifier_status_container);
        
        refreshPlayerStateIndicator();
    }

    private void refreshPlayerStateIndicator() {
        CoreMediaPlayer mediaPlayer = Engine.instance().getMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }
        ImageView notifierIconImageView = (ImageView) findViewById(R.id.view_player_notifier_icon);
        int notifierResourceId = R.drawable.playernotifier_icon_play; 
        if (!mediaPlayer.isPlaying()) {
            notifierResourceId = R.drawable.playernotifier_icon_pause;
        }
        notifierIconImageView.setBackgroundResource(notifierResourceId);
    }

    @Override
    public void onTime() {
        CoreMediaPlayer mp = Engine.instance().getMediaPlayer();
        if (mp != null) {
            FileDescriptor fd = mp.getCurrentFD();

            String status = "";
            refreshPlayerStateIndicator();
            if (fd != null) {
                status = fd.artist + " - " + fd.title;
                if (getVisibility() == View.GONE) {
                    setVisibility(View.VISIBLE);
                    startAnimation(showNotifierAnimation);
                }
            } else {
                if (getVisibility() == View.VISIBLE) {
                    startAnimation(hideNotifierAnimation);
                    setVisibility(View.GONE);
                }
            }
            if (!status.equals(lastStatusShown)) {
                statusText.setText(status);
                lastStatusShown = status;
                statusContainer.startAnimation(fromRightAnimation);
            }

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
