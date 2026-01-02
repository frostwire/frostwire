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
