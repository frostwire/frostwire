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
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.frostwire.android.R;
import com.andrew.apollo.utils.ThemeUtils;

/**
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@SuppressWarnings("deprecation")
public class BottomActionBar extends RelativeLayout {

    /**
     * Resource name used to theme the bottom action bar
     */
    private static final String BOTTOM_ACTION_BAR = "bottom_action_bar";

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @throws NameNotFoundException
     */
    public BottomActionBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        // Initialze the theme resources
        final ThemeUtils resources = new ThemeUtils(context);
        // Theme the bottom action bar
        setBackgroundDrawable(resources.getDrawable(BOTTOM_ACTION_BAR));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Theme the selector
        final LinearLayout bottomActionBar = (LinearLayout)findViewById(R.id.bottom_action_bar);
        bottomActionBar.setBackgroundDrawable(new HoloSelector(getContext()));
    }
}
