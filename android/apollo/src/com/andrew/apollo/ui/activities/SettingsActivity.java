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

package com.andrew.apollo.ui.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.android.R;
import com.andrew.apollo.cache.ImageCache;
import com.andrew.apollo.ui.fragments.ThemeFragment;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.andrew.apollo.widgets.ColorSchemeDialog;

/**
 * Settings.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@SuppressWarnings("deprecation")
public class SettingsActivity extends PreferenceActivity {

    /**
     * Image cache
     */
    private ImageCache mImageCache;

    private PreferenceUtils mPreferences;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fade it in
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Get the preferences
        mPreferences = PreferenceUtils.getInstance(this);

        // Initialze the image cache
        mImageCache = ImageCache.getInstance(this);

        // UP
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Add the preferences
        addPreferencesFromResource(R.xml.settings);

        // Interface settings
        initInterface();
        // Removes the cache entries
        deleteCache();
        // About
        showOpenSourceLicenses();
        // Update the version number
        try {
            final PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            findPreference("version").setSummary(packageInfo.versionName);
        } catch (final NameNotFoundException e) {
            findPreference("version").setSummary("?");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStart() {
        super.onStart();
        MusicUtils.notifyForegroundStateChanged(this, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStop() {
        super.onStop();
        MusicUtils.notifyForegroundStateChanged(this, false);
    }

    /**
     * Initializes the preferences under the "Interface" category
     */
    private void initInterface() {
        // Color scheme picker
        updateColorScheme();
        // Open the theme chooser
        openThemeChooser();
    }

    /**
     * Shows the {@link ColorSchemeDialog} and then saves the changes.
     */
    private void updateColorScheme() {
        final Preference colorScheme = findPreference("color_scheme");
        colorScheme.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                ApolloUtils.showColorPicker(SettingsActivity.this);
                return true;
            }
        });
    }

    /**
     * Opens the {@link ThemeFragment}.
     */
    private void openThemeChooser() {
        final Preference themeChooser = findPreference("theme_chooser");
        themeChooser.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final Intent themeChooserIntent = new Intent(SettingsActivity.this,
                        ThemesActivity.class);
                startActivity(themeChooserIntent);
                return true;
            }
        });
    }

    /**
     * Removes all of the cache entries.
     */
    private void deleteCache() {
        final Preference deleteCache = findPreference("delete_cache");
        deleteCache.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                new AlertDialog.Builder(SettingsActivity.this).setMessage(R.string.delete_warning)
                        .setPositiveButton(android.R.string.ok, new OnClickListener() {

                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                mImageCache.clearCaches();
                            }
                        }).setNegativeButton(R.string.cancel, new OnClickListener() {

                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                dialog.dismiss();
                            }
                        }).create().show();
                return true;
            }
        });
    }

    /**
     * Show the open source licenses
     */
    private void showOpenSourceLicenses() {
        final Preference mOpenSourceLicenses = findPreference("open_source");
        mOpenSourceLicenses.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(final Preference preference) {
                ApolloUtils.createOpenSourceDialog(SettingsActivity.this).show();
                return true;
            }
        });
    }
}
