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
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;

/**
 * A custom {@link ImageButton} that represents the "repeat" button.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 */
public final class RepeatButton extends AppCompatImageButton
        implements OnClickListener {

    private Runnable onClickedCallback;

    /**
     * @param context The {@link Context} to use
     * @param attrs   The attributes of the XML tag that is inflating the view.
     */
    public RepeatButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.holo_background_selector);
        // Control playback (cycle repeat modes)
        setOnClickListener(this);
    }

    public void setOnClickedCallback(Runnable runnable) {
        onClickedCallback = runnable;
    }

    @Override
    public void onClick(final View v) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, MusicUtils::cycleRepeat);
        updateRepeatState();
        if (onClickedCallback != null) {
            try {
                onClickedCallback.run();
            } catch (Throwable t) {
                // Handle exception if necessary
            }
        }
    }

    /**
     * Sets the correct drawable and tint for the repeat state.
     */
    public void updateRepeatState() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            int repeatMode = MusicUtils.getRepeatMode();
            SystemUtils.postToUIThread(() -> getRepeatModePost(repeatMode));
        });
    }

    private void getRepeatModePost(Integer repeatMode) {
        int iconResId;
        int tintColor;

        switch (repeatMode) {
            case MusicPlaybackService.REPEAT_ALL:
                setContentDescription(getResources().getString(R.string.accessibility_repeat_all));
                iconResId = R.drawable.btn_playback_repeat_all;
                tintColor = getRepeatEnabledColor(getContext());
                break;
            case MusicPlaybackService.REPEAT_CURRENT:
                setContentDescription(getResources().getString(R.string.accessibility_repeat_one));
                iconResId = R.drawable.btn_playback_repeat_one;
                tintColor = getRepeatEnabledColor(getContext());
                break;
            case MusicPlaybackService.REPEAT_NONE:
                setContentDescription(getResources().getString(R.string.accessibility_repeat));
                iconResId = R.drawable.btn_playback_repeat;
                tintColor = getRepeatDisabledColor(getContext());
                break;
            default:
                return;
        }
        setImageResource(iconResId);
        setImageTintList(ColorStateList.valueOf(tintColor));
    }

    // Method to retrieve the tint color when repeat is disabled
    private static int getRepeatDisabledColor(Context context) {
        return UIUtils.getAppIconPrimaryColor(context);
    }

    // Method to retrieve the tint color when repeat is enabled
    private static int getRepeatEnabledColor(Context context) {
        return ContextCompat.getColor(context, R.color.basic_blue_highlight); // Define in resources
    }
}
