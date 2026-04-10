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
import android.widget.ImageButton;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;

public final class RepeatButton extends AppCompatImageButton
        implements OnClickListener {

    private Runnable onClickedCallback;

    public RepeatButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.holo_background_selector);
        setOnClickListener(this);
    }

    public void setOnClickedCallback(Runnable runnable) {
        onClickedCallback = runnable;
    }

    @Override
    public void onClick(final View v) {
        MusicUtils.cycleRepeat();
        updateRepeatState();
        if (onClickedCallback != null) {
            try {
                onClickedCallback.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public void updateRepeatState() {
        int repeatMode = MusicUtils.getRepeatMode();
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

    private static int getRepeatDisabledColor(Context context) {
        return UIUtils.getAppIconPrimaryColor(context);
    }

    private static int getRepeatEnabledColor(Context context) {
        return ContextCompat.getColor(context, R.color.basic_blue_highlight);
    }
}
