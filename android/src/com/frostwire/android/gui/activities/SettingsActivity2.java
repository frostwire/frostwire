/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.activities;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity2;
import com.frostwire.android.gui.views.preference.CheckBoxSeedingPreference2;
import com.frostwire.android.gui.views.preference.NumberPickerPreference;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SettingsActivity2 extends AbstractActivity2
        implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    /**
     * When starting this activity, the invoking Intent can contain this extra
     * string to specify which fragment should be initially displayed.
     */
    public static final String EXTRA_SHOW_FRAGMENT =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT;

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specified to supply a Bundle of arguments to pass
     * to that fragment when it is instantiated during the initial creation
     * of the activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS;

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specify to supply the title to be shown for
     * that fragment.
     */
    public static final String EXTRA_SHOW_FRAGMENT_TITLE =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE;

    // keep this field as a starting point to support multipane settings in tablet
    // see PreferenceFragment source code
    private final boolean singlePane;

    public SettingsActivity2() {
        super(R.layout.activity_settings);
        singlePane = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String fragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        Bundle fragmentArgs = intent.getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        int fragmentTitle = intent.getIntExtra(EXTRA_SHOW_FRAGMENT_TITLE, 0);

        if (fragmentName == null) {
            fragmentName = SettingsActivity2.Application.class.getName();
        }

        switchToFragment(fragmentName, fragmentArgs, fragmentTitle);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        startPreferencePanel(pref.getFragment(), pref.getExtras(), pref.getTitleRes(), null, 0);
        return true;
    }

    private void startPreferencePanel(String fragmentClass, Bundle args, int titleRes,
                                      Fragment resultTo, int resultRequestCode) {
        if (singlePane) {
            startWithFragment(fragmentClass, args, titleRes, resultTo, resultRequestCode);
        } else {
            // check singlePane comment
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private void startWithFragment(String fragmentName, Bundle args, int titleRes,
                                   Fragment resultTo, int resultRequestCode) {
        Intent intent = buildStartFragmentIntent(fragmentName, args, titleRes);
        if (resultTo == null) {
            startActivity(intent);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    private Intent buildStartFragmentIntent(String fragmentName, Bundle args, int titleRes) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, titleRes);
        return intent;
    }

    private void switchToFragment(String fragmentName, Bundle args, int titleRes) {
        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.replace(R.id.activity_settings_content, f);
        transaction.commitAllowingStateLoss();

        if (titleRes != 0) {
            setTitle(titleRes);
        }
    }

    public static class Application extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_application);
        }
    }

    public static class Search extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_search);
        }
    }

    public static class Torrent extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_torrent);
        }

        @Override
        public void onResume() {
            super.onResume();

            setupTorrentOptions();
        }

        private void setupTorrentOptions() {
            final BTEngine e = BTEngine.getInstance();
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED, e, 0L, true);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED, e, 0L, true);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, e, -1L, false);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOADS, e, null, false);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS, e, null, false);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS, e, null, false);
        }

        //todo think about adding units
        private void setupNumericalPreference(final String key, final BTEngine e, final Long unlimitedValue, final boolean rate) {
            final NumberPickerPreference pickerPreference = (NumberPickerPreference) findPreference(key);
            if (pickerPreference != null) {
                pickerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (e != null) {
                            int newVal = (int) newValue;
                            executeBTEngineAction(key, e, newVal);
                            displayNumericalSummaryForPreference(preference, newVal, unlimitedValue, rate);
                            return true;
                        }
                        return false;
                    }
                });
                displayNumericalSummaryForPreference(pickerPreference, ConfigurationManager.instance().getLong(key), unlimitedValue, rate);
            }
        }

        private void executeBTEngineAction(final String key, final BTEngine e, final int value) {
            switch (key) {
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED:
                    e.downloadRateLimit(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED:
                    e.uploadRateLimit(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS:
                    e.maxActiveDownloads(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOADS:
                    e.maxActiveSeeds(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS:
                    e.maxConnections(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_PEERS:
                    e.maxPeers(value);
                    break;
            }
        }

        private void displayNumericalSummaryForPreference(Preference preference, long value, Long unlimitedValue, boolean rate) {
            if (unlimitedValue != null && value == unlimitedValue) {
                preference.setSummary(R.string.unlimited);
            } else {
                if (rate) {
                    preference.setSummary(UIUtils.getBytesInHuman(value));
                } else {
                    preference.setSummary(String.valueOf(value));
                }
            }
        }

    }

    public static class Other extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_other);
        }
    }
}
