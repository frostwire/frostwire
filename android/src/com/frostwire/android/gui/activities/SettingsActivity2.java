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
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.fragments.preference.ApplicationFragment;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity2;
import com.frostwire.android.gui.views.preference.ButtonActionPreference;
import com.frostwire.android.gui.views.preference.ButtonActionPreference2;
import com.frostwire.android.gui.views.preference.StoragePreference;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.uxstats.UXStats;

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

        @Override
        public void onResume() {
            super.onResume();

            setupPermanentStatusNotificationOption();
            setupHapticFeedback();
            setupUXStatsOption();
            setupClearIndex();
        }

        private void setupPermanentStatusNotificationOption() {
            final CheckBoxPreference enablePermanentStatusNotification = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION);
            if (enablePermanentStatusNotification != null) {
                enablePermanentStatusNotification.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final boolean notificationEnabled = (boolean) newValue;
                        if (!notificationEnabled) {
                            NotificationManager notificationService = (NotificationManager) getActivity().getSystemService(NOTIFICATION_SERVICE);
                            if (notificationService != null) {
                                notificationService.cancel(EngineService.FROSTWIRE_STATUS_NOTIFICATION);
                            }
                        }
                        return true;
                    }
                });
            }
        }

        private void setupHapticFeedback() {
            final CheckBoxPreference preference = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_HAPTIC_FEEDBACK_ON);
            if (preference != null) {
                preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        CheckBoxPreference cbPreference = (CheckBoxPreference) preference;
                        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_HAPTIC_FEEDBACK_ON, cbPreference.isChecked());
                        Engine.instance().getVibrator().onPreferenceChanged();
                        return true;
                    }
                });
            }
        }

        private void setupUXStatsOption() {
            final CheckBoxPreference checkPref = (CheckBoxPreference) findPreference(Constants.PREF_KEY_UXSTATS_ENABLED);
            if (checkPref != null) {
                checkPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean newVal = (Boolean) newValue;
                        if (!newVal) { // not send ux stats
                            UXStats.instance().setContext(null);
                        }
                        //todo re-enable UXStats on checking that box
                        //currently (2.I.17) after checking that box the next time the main screen will be resumed
                        //it will check for the updates (IF there were at least 30 minutes since last update check)
                        //and during that update it might turn on the ux stats.
                        return true;
                    }
                });
            }
        }

        private void setupClearIndex() {
            final ButtonActionPreference2 preference = (ButtonActionPreference2) findPreference("frostwire.prefs.internal.clear_index");

            if (preference != null) {
                updateIndexSummary(preference);
                preference.setOnActionListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        LocalSearchEngine.instance().clearCache();
                        UIUtils.showShortMessage(getActivity(), R.string.deleted_crawl_cache);
                        updateIndexSummary(preference);
                    }
                });
            }
        }

        private void updateIndexSummary(ButtonActionPreference2 preference) {
            float size = (((float) LocalSearchEngine.instance().getCacheSize()) / 1024) / 1024;
            preference.setSummary(getString(R.string.crawl_cache_size, size));
        }
    }
}
