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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.andrew.apollo.utils.ThemeUtils;

/**
 * This is a custom {@link FrameLayout} that is used as the main conent when
 * transacting fragments that is made themeable by allowing developers to change
 * the background.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ThemeableFrameLayout extends FrameLayout {

    /**
     * Used to set the background
     */
    public static final String BACKGROUND = "pager_background";

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    @SuppressWarnings("deprecation")
    public ThemeableFrameLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Initialze the theme resources
        final ThemeUtils resources = new ThemeUtils(context);
        // Theme the layout
        setBackgroundDrawable(resources.getDrawable(BACKGROUND));
    }

}
