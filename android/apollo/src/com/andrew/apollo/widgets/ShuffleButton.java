/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2018, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.widgets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.Asyncs;

/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon
 */
public final class ShuffleButton extends AppCompatImageButton
        implements OnClickListener {

    private Runnable onClickedCallback;

    /**
     * @param context The {@link Context} to use
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public ShuffleButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.holo_background_selector);
        // Control playback (cycle shuffle)
        setOnClickListener(this);
    }

    public void setOnClickedCallback(Runnable runnable) {
        onClickedCallback = runnable;
    }

    @Override
    public void onClick(final View v) {
        MusicUtils.cycleShuffle();
        updateShuffleState();
        if (onClickedCallback != null) {
            try {
                onClickedCallback.run();
            } catch (Throwable t) {
                // Handle exception if necessary
            }
        }
    }

    /**
     * Sets the correct drawable and tint for the shuffle state.
     */
    public void updateShuffleState() {
        Asyncs.async(MusicUtils::isShuffleEnabled, ShuffleButton::isShuffleEnabledPost, this);
    }

    private void isShuffleEnabledPost(Boolean shuffleEnabled) {
        int iconResId = R.drawable.btn_playback_shuffle; // Use the same icon for both states
        setImageResource(iconResId);

        if (shuffleEnabled) {
            // When shuffle is enabled, set the tint color to blue
            setImageTintList(ColorStateList.valueOf(getShuffleEnabledColor(getContext())));
        } else {
            // When shuffle is disabled, apply the default tint color
            setImageTintList(ColorStateList.valueOf(getShuffleDisabledTintColor(getContext())));
        }
        setContentDescription(getResources().getString(shuffleEnabled ? R.string.accessibility_shuffle_all : R.string.accessibility_shuffle));
    }

    // Method to retrieve the tint color when shuffle is disabled
    private static int getShuffleDisabledTintColor(Context context) {
        return ContextCompat.getColor(context, R.color.app_icon_primary); // Your default tint color
    }

    // Method to retrieve the color when shuffle is enabled
    private static int getShuffleEnabledColor(Context context) {
        return ContextCompat.getColor(context, R.color.basic_blue_highlight); // The blue color you want
    }
}
