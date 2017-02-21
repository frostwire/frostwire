/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.Menu;
import android.view.MenuItem;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;

/**
 * In order to implement the theme chooser for Apollo, this class returns a
 * {@link Resources} object that can be used like normal. In other words, when
 * {@code getDrawable()} or {@code getColor()} is called, the object returned is
 * from the current theme package name and because all of the theme resource
 * identifiers are the same as all of Apollo's resources a little less code is
 * used to implement the theme chooser.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ThemeUtils {

    /**
     * This is the current theme color as set by the color picker.
     */
    private final int mCurrentThemeColor;

    /**
     * The theme resources.
     */
    private Resources mResources;

    /**
     * Constructor for <code>ThemeUtils</code>
     *
     * @param context The {@link Context} to use.
     */
    public ThemeUtils(final Context context) {
        // Find the theme resources
        mResources = context.getResources();
        // Get the current theme color
        mCurrentThemeColor = PreferenceUtils.getInstance(context).getDefaultThemeColor(context);
    }

    /**
     * Used to return a color from the theme resources.
     *
     * @param resourceName The name of the color to return. i.e.
     *                     "action_bar_color".
     * @return A new color from the theme resources.
     */
    public int getColor(final String resourceName) {
        if (mResources != null) {
            try {
                final int resourceId = mResources.getIdentifier(resourceName, "color", BuildConfig.APPLICATION_ID);
                if (resourceId != 0) { // if not, the color is not here
                    return mResources.getColor(resourceId);
                }
            } catch (final Resources.NotFoundException e) {
                // If the theme designer wants to allow the user to theme a
                // particular object via the color picker, they just remove the
                // resource item from the themeconfig.xml file.
            }
        }
        return mCurrentThemeColor;
    }

    /**
     * Used to return a drawable from the theme resources.
     *
     * @param resourceName The name of the drawable to return. i.e.
     *                     "pager_background".
     * @return A new color from the theme resources.
     */
    public Drawable getDrawable(final String resourceName) {
        if (mResources != null) {
            final int resourceId = mResources.getIdentifier(resourceName, "drawable", BuildConfig.APPLICATION_ID);
            try {
                return mResources.getDrawable(resourceId);
            } catch (final Resources.NotFoundException e) {
                //$FALL-THROUGH$
            }
        }
        return null;
    }

    /**
     * Sets the {@link MenuItem} icon for the favorites action.
     *
     * @param favorite The favorites action.
     */
    public static void setFavoriteIcon(final Menu favorite) {
        final MenuItem item = favorite.findItem(R.id.menu_player_favorite);
        item.setIcon(MusicUtils.isFavorite() ?
                R.drawable.ic_action_favorite_selected : R.drawable.ic_action_favorite);
    }
}
