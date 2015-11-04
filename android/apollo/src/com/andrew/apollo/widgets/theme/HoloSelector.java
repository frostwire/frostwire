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

package com.andrew.apollo.widgets.theme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;

import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.ThemeUtils;

import java.lang.ref.WeakReference;

/**
 * A themeable {@link StateListDrawable}.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class HoloSelector extends StateListDrawable {

    /**
     * Used to theme the touched and focused colors
     */
    private static final String RESOURCE_NAME = "holo_selector";

    /**
     * Focused state
     */
    private static final int FOCUSED = android.R.attr.state_focused;

    /**
     * Pressed state
     */
    private static final int PRESSED = android.R.attr.state_pressed;

    /**
     * Constructor for <code>HoloSelector</code>
     * 
     * @param context The {@link Context} to use.
     */
    @SuppressLint("NewApi")
    public HoloSelector(final Context context) {
        final ThemeUtils resources = new ThemeUtils(context);
        final int themeColor = resources.getColor(RESOURCE_NAME);
        // Focused
        addState(new int[] {
            FOCUSED
        }, makeColorDrawable(themeColor));
        // Pressed
        addState(new int[] {
            PRESSED
        }, makeColorDrawable(themeColor));
        // Default
        addState(new int[] {}, makeColorDrawable(Color.TRANSPARENT));
        setExitFadeDuration(400);
    }

    /**
     * @param color The color to use.
     * @return A new {@link ColorDrawable}.
     */
    private static final ColorDrawable makeColorDrawable(final int color) {
        return new WeakReference<ColorDrawable>(new ColorDrawable(color)).get();
    }
}
