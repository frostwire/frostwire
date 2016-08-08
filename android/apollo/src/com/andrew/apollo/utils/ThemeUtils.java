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

package com.andrew.apollo.utils;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
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
     * Default package name.
     */
    public static final String APOLLO_PACKAGE = BuildConfig.APPLICATION_ID;

    /**
     * Current theme package name.
     */
    public static final String PACKAGE_NAME = "theme_package_name";

    /**
     * Used to get and set the theme package name.
     */
    private final SharedPreferences mPreferences;

    /**
     * The theme package name.
     */
    private final String mThemePackage;

    /**
     * This is the current theme color as set by the color picker.
     */
    private final int mCurrentThemeColor;

    /**
     * Package manager
     */
    private final PackageManager mPackageManager;

    /**
     * Custom action bar layout
     */
    private final View mActionBarLayout;

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
        // Get the preferences
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        // Get the theme package name
        mThemePackage = getThemePackageName();
        // Initialize the package manager
        mPackageManager = context.getPackageManager();
        try {
            // Find the theme resources
            mResources = mPackageManager.getResourcesForApplication(mThemePackage);
        } catch (Throwable e) {
            // If the user isn't using a theme, then the resources should be
            // Apollo's.
            setThemePackageName(APOLLO_PACKAGE);
        }
        // Get the current theme color
        mCurrentThemeColor = PreferenceUtils.getInstance(context).getDefaultThemeColor(context);
        // Inflate the custom layout
        mActionBarLayout = LayoutInflater.from(context).inflate(R.layout.action_bar, null);
    }

    /**
     * Set the new theme package name.
     *
     * @param packageName The package name of the theme to be set.
     */
    public void setThemePackageName(final String packageName) {
        ApolloUtils.execute(new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... unused) {
                final SharedPreferences.Editor editor = mPreferences.edit();
                editor.putString(PACKAGE_NAME, packageName);
                editor.apply();
                return null;
            }
        }, (Void[]) null);
    }

    /**
     * Return the current theme package name.
     *
     * @return The default theme package name.
     */
    public final String getThemePackageName() {
        return mPreferences.getString(PACKAGE_NAME, APOLLO_PACKAGE);
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
                final int resourceId = mResources.getIdentifier(resourceName, "color", mThemePackage);
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
            final int resourceId = mResources.getIdentifier(resourceName, "drawable", mThemePackage);
            try {
                return mResources.getDrawable(resourceId);
            } catch (final Resources.NotFoundException e) {
                //$FALL-THROUGH$
            }
        }
        return null;
    }

    /**
     * Sets the correct overflow icon in the action bar depending on whether or
     * not the current action bar color is dark or light.
     *
     * @param app The {@link Activity} used to set the theme.
     */
    public void setOverflowStyle(final Activity app) {
        app.setTheme(R.style.Apollo_Theme_Dark);
    }

    /**
     * This is used to set the color of a {@link MenuItem}. For instance, when
     * the current song is a favorite, the favorite icon will use the current
     * theme color.
     *
     * @param menuItem             The {@link MenuItem} to set.
     * @param resourceColorName    The color theme resource key.
     * @param resourceDrawableName The drawable theme resource key.
     */
    public void setMenuItemColor(final MenuItem menuItem, final String resourceColorName,
                                 final String resourceDrawableName) {

        final Drawable maskDrawable = getDrawable(resourceDrawableName);
        if (!(maskDrawable instanceof BitmapDrawable)) {
            return;
        }

        final Bitmap maskBitmap = ((BitmapDrawable) maskDrawable).getBitmap();
        final int width = maskBitmap.getWidth();
        final int height = maskBitmap.getHeight();

        final Bitmap outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(outBitmap);
        canvas.drawBitmap(maskBitmap, 0, 0, null);

        final Paint maskedPaint = new Paint();
        maskedPaint.setColor(getColor(resourceColorName));
        maskedPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));

        canvas.drawRect(0, 0, width, height, maskedPaint);

        final BitmapDrawable outDrawable = new BitmapDrawable(mResources, outBitmap);
        menuItem.setIcon(outDrawable);
    }

    /**
     * Sets the {@link MenuItem} icon for the favorites action.
     *
     * @param context  The {@link Context} to use.
     * @param favorite The favorites action.
     */
    public void setFavoriteIcon(final Menu favorite) {
        final MenuItem favoriteAction = favorite.findItem(R.id.menu_favorite);
        final String favoriteIconId = "ic_action_favorite";
        if (MusicUtils.isFavorite()) {
            setMenuItemColor(favoriteAction, "favorite_selected", favoriteIconId);
        } else {
            setMenuItemColor(favoriteAction, "favorite_normal", favoriteIconId);
        }
    }

    /**
     * Sets the {@link MenuItem} icon for the search action.
     *
     * @param context The {@link Context} to use.
     * @param search  The Menu used to find the "menu_search" action.
     */
    public void setSearchIcon(final Menu search) {
        final MenuItem searchAction = search.findItem(R.id.menu_search);
        final String searchIconId = "ic_action_search";
        setMenuItemColor(searchAction, "search_action", searchIconId);
    }

    /**
     * Sets the {@link MenuItem} icon for the add to Home screen action.
     *
     * @param context The {@link Context} to use.
     * @param search  The Menu used to find the "add_to_homescreen" item.
     */
    public void setAddToHomeScreenIcon(final Menu search) {
        final MenuItem pinnAction = search.findItem(R.id.menu_add_to_homescreen);
        final String pinnIconId = "ic_action_pinn_to_home";
        setMenuItemColor(pinnAction, "pinn_to_action", pinnIconId);
    }

    /**
     * Builds a custom layout and applies it to the action bar, then themes the
     * background, title, and subtitle.
     *
     * @param actionBar The {@link ActionBar} to use.
     * @param resources The {@link ThemeUtils} used to theme the background,
     *                  title, and subtitle.
     * @param title     The title for the action bar
     * @param subtitle  The subtitle for the action bar.
     */
    public void themeActionBar(final ActionBar actionBar, final String title) {
        // Set the custom layout
        actionBar.setCustomView(mActionBarLayout);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);

        // Theme the action bar background
        actionBar.setBackgroundDrawable(getDrawable("action_bar"));

        // Theme the title
        setTitle(title);
    }

    /**
     * Themes the action bar subtitle
     *
     * @param subtitle The subtitle to use
     */
    public void setTitle(final String title) {
        if (!TextUtils.isEmpty(title)) {
            // Get the title text view
            final TextView actionBarTitle = (TextView) mActionBarLayout
                    .findViewById(R.id.action_bar_title);
            // Theme the title
            actionBarTitle.setTextColor(getColor("action_bar_title"));
            // Set the title
            actionBarTitle.setText(title);
        }
    }

    /**
     * Themes the action bar subtitle
     *
     * @param subtitle The subtitle to use
     */
    public void setSubtitle(final String subtitle) {
        if (!TextUtils.isEmpty(subtitle)) {
            final TextView actionBarSubtitle = (TextView) mActionBarLayout
                    .findViewById(R.id.action_bar_subtitle);
            actionBarSubtitle.setVisibility(View.VISIBLE);
            // Theme the subtitle
            actionBarSubtitle.setTextColor(getColor("action_bar_subtitle"));
            // Set the subtitle
            actionBarSubtitle.setText(subtitle);
        }
    }
}
