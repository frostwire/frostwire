/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Grzesiek Rzaca (grzesiekrzaca)
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
import android.preference.PreferenceActivity;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.frostwire.android.R;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.fragments.preference.ApplicationFragment;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity2;
import com.frostwire.android.gui.views.preference.StoragePreference;
import com.frostwire.bittorrent.BTEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 * @author grzesiekrzaca
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
        CharSequence fragmentTitle = intent.getCharSequenceExtra(EXTRA_SHOW_FRAGMENT_TITLE);

        if (fragmentName == null) {
            fragmentName = ApplicationFragment.class.getName();
        }

        switchToFragment(fragmentName, fragmentArgs, fragmentTitle);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        startPreferencePanel(pref.getFragment(), pref.getExtras(), pref.getTitle(), null, 0);
        return true;
    }

    private void startPreferencePanel(String fragmentClass, Bundle args, CharSequence title,
                                      Fragment resultTo, int resultRequestCode) {
        if (singlePane) {
            startWithFragment(fragmentClass, args, title, resultTo, resultRequestCode);
        } else {
            // check singlePane comment
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private void startWithFragment(String fragmentName, Bundle args, CharSequence title,
                                   Fragment resultTo, int resultRequestCode) {
        Intent intent = buildStartFragmentIntent(fragmentName, args, title);
        if (resultTo == null) {
            startActivity(intent);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    private Intent buildStartFragmentIntent(String fragmentName, Bundle args, CharSequence title) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, title);
        return intent;
    }

    private void switchToFragment(String fragmentName, Bundle args, CharSequence title) {
        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.replace(R.id.activity_settings_content, f);
        transaction.commitAllowingStateLoss();

        if (title != null) {
            setTitle(title);
        }
    }

    @Override
    public void startActivity(Intent intent) {
        if (intent != null && StoragePicker.ACTION_OPEN_DOCUMENT_TREE.equals(intent.getAction())) {
            StoragePicker.show(this);
        } else {
            super.startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == StoragePicker.SELECT_FOLDER_REQUEST_CODE) {
            StoragePreference.onDocumentTreeActivityResult(this, requestCode, resultCode, data);
            // TODO: missing summary update
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public static class Search extends PreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.settings_search);
        }

        @Override
        public void onResume() {
            super.onResume();
            setupSearchEngines();
        }

        private void setupSearchEngines() {
            final PreferenceScreen searchEnginesScreen = (PreferenceScreen) findPreference(Constants.PREF_KEY_SEARCH_PREFERENCE_CATEGORY);
            final Map<CheckBoxPreference, SearchEngine> inactiveSearchPreferences = new HashMap<>();
            final Map<CheckBoxPreference, SearchEngine> activeSearchEnginePreferences = new HashMap<>();
            getSearchEnginePreferences(inactiveSearchPreferences, activeSearchEnginePreferences);

            // Change listener for the all search engines. Checks or unchecks the SelectAll checkbox
            final Preference.OnPreferenceChangeListener onPreferenceChangeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ToggleAllSearchEnginesPreference2 selectAll = (ToggleAllSearchEnginesPreference2) findPreference("frostwire.prefs.search.preference_category.select_all");

                    return selectAll.requestChildStateChange((CheckBoxPreference) preference);
                }
            };

            // Hide inactive search engines and setup click listeners to interact with Select All.
            if (searchEnginesScreen != null) {
                for (CheckBoxPreference preference : inactiveSearchPreferences.keySet()) {
                    searchEnginesScreen.removePreference(preference);
                }
            }

            for (CheckBoxPreference preference : activeSearchEnginePreferences.keySet()) {
                preference.setOnPreferenceChangeListener(onPreferenceChangeListener);
            }

            ToggleAllSearchEnginesPreference2 selectAll = (ToggleAllSearchEnginesPreference2) findPreference("frostwire.prefs.search.preference_category.select_all");
            selectAll.setSearchEnginePreferences(activeSearchEnginePreferences);

        }

        private void getSearchEnginePreferences(Map<CheckBoxPreference, SearchEngine> inactiveSearchEnginePreferences, Map<CheckBoxPreference, SearchEngine> activeSearchEnginePreferences) {
            // make sure we start empty
            inactiveSearchEnginePreferences.clear();
            activeSearchEnginePreferences.clear();

            for (SearchEngine engine : SearchEngine.getEngines()) {
                CheckBoxPreference preference = (CheckBoxPreference) findPreference(engine.getPreferenceKey());
                if (preference != null) { //it could already have been removed due to remote config value.
                    if (engine.isActive()) {
                        activeSearchEnginePreferences.put(preference, engine);
                    } else {
                        inactiveSearchEnginePreferences.put(preference, engine);
                    }
                }
            }
        }
    }

    public static class Torrent extends PreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.settings_torrent);
        }

        @Override
        public void onResume() {
            super.onResume();
            setupTorrentOptions();
        }

        private void setupTorrentOptions() {
            final BTEngine e = BTEngine.getInstance();
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED, e, 0L, true, null);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED, e, 0L, true, null);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, e, -1L, false, Unit.DOWNLOADS);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOADS, e, null, false, Unit.UPLOADS);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS, e, null, false, Unit.CONNECTIONS);
            setupNumericalPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS, e, null, false, Unit.PEERS);
        }

        private void setupNumericalPreference(final String key, final BTEngine btEngine, final Long unlimitedValue, final boolean byteRate, final Unit unit) {
            // TODO: restore
//            final NumberPickerPreference pickerPreference = (NumberPickerPreference) findPreference(key);
//            if (pickerPreference != null) {
//                pickerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
//                    @Override
//                    public boolean onPreferenceChange(Preference preference, Object newValue) {
//                        if (btEngine != null) {
//                            int newVal = (int) newValue;
//                            executeBTEngineAction(key, btEngine, newVal);
//                            displayNumericalSummaryForPreference(preference, newVal, unlimitedValue, byteRate, unit);
//                            return checkBTEngineActionResult(key, btEngine, newVal);
//                        }
//                        return false;
//                    }
//                });
//                displayNumericalSummaryForPreference(pickerPreference, ConfigurationManager.instance().getLong(key), unlimitedValue, byteRate, unit);
//            }
        }

        private void executeBTEngineAction(final String key, final BTEngine btEngine, final int value) {
            switch (key) {
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED:
                    btEngine.downloadRateLimit(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED:
                    btEngine.uploadRateLimit(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS:
                    btEngine.maxActiveDownloads(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOADS:
                    btEngine.maxActiveSeeds(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS:
                    btEngine.maxConnections(value);
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_PEERS:
                    btEngine.maxPeers(value);
                    break;
            }
        }

        private boolean checkBTEngineActionResult(final String key, final BTEngine btEngine, final int value) {
            switch (key) {
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED:
                    return btEngine.downloadRateLimit() == value;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED:
                    return btEngine.uploadRateLimit() == value;
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS:
                    return btEngine.maxActiveDownloads() == value;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOADS:
                    return btEngine.maxActiveSeeds() == value;
                case Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS:
                    return btEngine.maxConnections() == value;
                case Constants.PREF_KEY_TORRENT_MAX_PEERS:
                    return btEngine.maxPeers() == value;
            }
            return false;
        }

        private void displayNumericalSummaryForPreference(Preference preference, long value, Long unlimitedValue, boolean rate, Unit unit) {
            if (unlimitedValue != null && value == unlimitedValue) {
                preference.setSummary(R.string.unlimited);
            } else {
                if (rate) {
                    preference.setSummary(UIUtils.getBytesInHuman(value));
                } else {
                    preference.setSummary(getValueWithUnit(unit, value));
                }
            }
        }

        private String getValueWithUnit(Unit unit, long value) {
            if (unit != null) {
                return getActivity().getResources().getQuantityString(unit.getPluralResource(), (int) value, value);
            }
            return String.valueOf(value);
        }

        public enum Unit {
            DOWNLOADS(R.plurals.unit_downloads),
            UPLOADS(R.plurals.unit_uploads),
            CONNECTIONS(R.plurals.unit_connections),
            PEERS(R.plurals.unit_peers);

            private int pluralResource;

            Unit(int pluralResource) {
                this.pluralResource = pluralResource;
            }

            public int getPluralResource() {
                return pluralResource;
            }
        }

    }

    public static class Other extends PreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.settings_other);
        }
    }
}
