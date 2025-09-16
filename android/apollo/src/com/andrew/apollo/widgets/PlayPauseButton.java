/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andrew.apollo.widgets;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import androidx.appcompat.widget.AppCompatImageButton;

import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.SystemUtils;

/**
 * A custom {@link AppCompatImageButton} that represents the "play and pause" button.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class PlayPauseButton extends AppCompatImageButton
        implements OnClickListener, OnLongClickListener {

    private int playDrawable;
    private int pauseDrawable;
    private boolean hasLongClickListener;

    /**
     * @param context The {@link Context} to use
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public PlayPauseButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.holo_background_selector);
        // Control playback (play/pause)
        setOnClickListener(this);
        // Show the cheat sheet
        setOnLongClickListener(this);

        this.playDrawable = R.drawable.btn_playback_play;
        this.pauseDrawable = R.drawable.btn_playback_pause;
    }

    @Override
    public void onClick(final View v) {
        MusicUtils.playPauseOrResume();
        updateState();
    }

    @Override
    public boolean onLongClick(final View view) {
        if (TextUtils.isEmpty(view.getContentDescription())) {
            return false;
        } else {
            ApolloUtils.showCheatSheet(view);
            return true;
        }
    }

    public void setPlayDrawable(int resId) {
        this.playDrawable = resId;
    }

    public void setPauseDrawable(int resId) {
        this.pauseDrawable = resId;
    }

    /**
     * Sets the correct drawable for playback.
     */
    public void updateState() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            boolean isPlaying = MusicUtils.isPlaying();
            SystemUtils.postToUIThread(() -> updateStatePost(isPlaying));
        });
    }

    public void setOnLongClickListener(OnLongClickListener l) {
        super.setOnLongClickListener(l);
        hasLongClickListener = l != null;
    }

    public boolean hasOnLongClickListener() {
        return hasLongClickListener;
    }

    private void updateStatePost(Boolean isPlaying) {
        if (isPlaying) {
            setContentDescription(getResources().getString(R.string.accessibility_pause));
            setImageResource(pauseDrawable);
        } else {
            setContentDescription(getResources().getString(R.string.accessibility_play));
            setImageResource(playDrawable);
        }
    }
}
