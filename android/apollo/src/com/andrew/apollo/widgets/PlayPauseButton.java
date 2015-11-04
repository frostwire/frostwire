/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.widgets;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;

import android.widget.Toast;
import com.frostwire.android.R;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.ThemeUtils;
import com.andrew.apollo.widgets.theme.HoloSelector;
import com.frostwire.android.gui.util.UIUtils;

/**
 * A custom {@link ImageButton} that represents the "play and pause" button.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class PlayPauseButton extends ImageButton implements OnClickListener, OnLongClickListener {

    /**
     * Play button theme resource
     */
    private static final String PLAY = "btn_playback_play";

    /**
     * Pause button theme resource
     */
    private static final String PAUSE = "btn_playback_pause";

    /**
     * The resources to use.
     */
    private final ThemeUtils mResources;

    private int playDrawable;
    private int pauseDrawable;

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    @SuppressWarnings("deprecation")
    public PlayPauseButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Initialze the theme resources
        mResources = new ThemeUtils(context);
        // Theme the selector
        setBackgroundDrawable(new HoloSelector(context));
        // Control playback (play/pause)
        setOnClickListener(this);
        // Show the cheat sheet
        setOnLongClickListener(this);

        this.playDrawable = 0;
        this.pauseDrawable = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(final View v) {
        MusicUtils.playOrPause();
        updateState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onLongClick(final View view) {
        if (TextUtils.isEmpty(view.getContentDescription())) {
            return false;
        } else {
            ApolloUtils.showCheatSheet(view);
            return true;
        }
    }

    /**
     * Sets the correct drawable for playback.
     */
    public void updateState() {
        if (MusicUtils.isPlaying()) {
            setContentDescription(getResources().getString(R.string.accessibility_pause));
            if (pauseDrawable == 0) {
                setImageDrawable(mResources.getDrawable(PAUSE));
            } else {
                setImageResource(pauseDrawable);
            }
        } else {
            setContentDescription(getResources().getString(R.string.accessibility_play));
            if (playDrawable == 0) {
                setImageDrawable(mResources.getDrawable(PLAY));
            } else {
                setImageResource(playDrawable);
            }
            //UIUtils.showToastMessage(getContext(), getContext().getString(R.string.player_paused_press_and_hold_to_stop), Toast.LENGTH_SHORT, Gravity.CENTER_VERTICAL, 0, 10);
        }
    }

    public void setPlayDrawable(int resId) {
        this.playDrawable = resId;
        updateState();
    }

    public void setPauseDrawable(int resId) {
        this.pauseDrawable = resId;
        updateState();
    }
}
