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
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;

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
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            final boolean isShuffling = MusicUtils.isShuffleEnabled();
            SystemUtils.postToUIThread(() -> isShuffleEnabledPost(isShuffling));
        });
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
        return UIUtils.getAppIconPrimaryColor(context);
    }

    // Method to retrieve the color when shuffle is enabled
    private static int getShuffleEnabledColor(Context context) {
        return ContextCompat.getColor(context, R.color.basic_blue_highlight); // The blue color you want
    }
}
