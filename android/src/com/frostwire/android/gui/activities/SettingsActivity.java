/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Emil Suleymanov (sssemil)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.*;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.StoragePicker;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.preference.NumberPickerPreference;
import com.frostwire.android.gui.views.preference.SimpleActionPreference;
import com.frostwire.android.gui.views.preference.StoragePreference;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.logging.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

/**
 * See {@link ConfigurationManager}
 *
 * @author gubatron
 * @author aldenml
 * @author sssemil
 */
public class SettingsActivity extends PreferenceActivity {

    private static final Logger LOG = Logger.getLogger(SettingsActivity.class);
    private static String currentPreferenceKey = null;

    @Override
    protected void onResume() {
        super.onResume();
        setupComponents();
        initializePreferenceScreen(getPreferenceScreen());
        if (currentPreferenceKey != null) {
            onPreferenceTreeClick(getPreferenceScreen(), getPreferenceManager().findPreference(currentPreferenceKey));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.application_preferences);

        hideActionBarIcon(getActionBar());

        setupComponents();

        String action = getIntent().getAction();
        if (action != null && action.equals(Constants.ACTION_SETTINGS_SELECT_STORAGE)) {
            getIntent().setAction(null);
            StoragePreference.invokeStoragePreference(this);
        }

        updateConnectSwitch();
    }

    private void hideActionBarIcon(ActionBar bar) {
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(false);
            bar.setDisplayShowTitleEnabled(true);
            bar.setIcon(android.R.color.transparent);
        }
    }

    private void setupComponents() {
        setupConnectSwitch();
        setupStorageOption();
        setupOtherOptions();
        setupSeedingOptions();
        setupTorrentOptions();
        setupClearIndex();
        setupSearchEngines();
        setupUXStatsOption();
        setupAbout();
    }

    private void setupTorrentOptions() {
        final BTEngine e = BTEngine.getInstance();
        setupTorrentMaxDownloadSpeed(e);
        setupTorrentMaxUploadSpeed(e);
        setupTorrentMaxDownloads(e);
        setupTorrentMaxUploads(e);
        setupTorrentMaxTotalConnections(e);
        setupTorrentMaxPeers(e);
    }

    private void setupTorrentMaxDownloadSpeed(final BTEngine e) {
        NumberPickerPreference pickerPref = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED);
        if (pickerPref != null) {
            pickerPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (e != null) {
                        e.setDownloadSpeedLimit((int) newValue);
                        return e.getDownloadSpeedLimit() == (int) newValue;
                    }
                    return false;
                }
            });
        }
    }

    private void setupTorrentMaxUploadSpeed(final BTEngine e) {
        NumberPickerPreference pickerPref = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED);
        if (pickerPref != null) {
            pickerPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (e != null) {
                        e.setUploadSpeedLimit((int) newValue);
                        return e.getUploadSpeedLimit() == (int) newValue;
                    }
                    return false;
                }
            });
        }
    }

    private void setupTorrentMaxDownloads(final BTEngine e) {
        NumberPickerPreference pickerPref = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS);
        if (pickerPref != null) {
            pickerPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (e != null) {
                        e.setMaxActiveDownloads((int) newValue);
                        return e.getMaxActiveDownloads() == (int) newValue;
                    }
                    return false;
                }
            });
        }
    }

    private void setupTorrentMaxUploads(final BTEngine e) {
        NumberPickerPreference pickerPref = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOADS);
        if (pickerPref != null) {
            pickerPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (e != null) {
                        e.setMaxActiveSeeds((int) newValue);
                        return e.getMaxActiveSeeds() == (int) newValue;
                    }
                    return false;
                }
            });
        }
    }

    private void setupTorrentMaxTotalConnections(final BTEngine e) {
        NumberPickerPreference pickerPreference = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS);
        if (pickerPreference != null) {
            pickerPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (e != null) {
                        e.setMaxConnections((int) newValue);
                        return e.getMaxConnections() == (int) newValue;
                    }
                    return false;
                }
            });
        }
    }

    private void setupTorrentMaxPeers(final BTEngine e) {
        NumberPickerPreference pickerPreference = (NumberPickerPreference) findPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS);
        if (pickerPreference != null) {
            pickerPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (e != null) {
                        e.setMaxPeers((int) newValue);
                        return e.getMaxPeers() == (int) newValue;
                    }
                    return false;
                }
            });
        }
    }


    private void setupOtherOptions() {
        setupPermanentStatusNotificationOption();
    }

    private void setupPermanentStatusNotificationOption() {
        final CheckBoxPreference enablePermanentStatusNotification = (CheckBoxPreference) findPreference(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION);
        if (enablePermanentStatusNotification != null) {
            enablePermanentStatusNotification.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean notificationEnabled = (boolean) newValue;
                    if (!notificationEnabled) {
                        NotificationManager notificationService = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        if (notificationService != null) {
                            notificationService.cancel(EngineService.FROSTWIRE_STATUS_NOTIFICATION);
                        }
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupSeedingOptions() {
        final CheckBoxPreference preferenceSeeding = (CheckBoxPreference)
                findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);

        // our custom preference, only so that we can change its status, or hide it.
        final com.frostwire.android.gui.views.preference.CheckBoxPreference preferenceSeedingWifiOnly = (com.frostwire.android.gui.views.preference.CheckBoxPreference)
                findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);

        if (preferenceSeeding != null) {
            preferenceSeeding.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean newVal = (Boolean) newValue;

                    if (!newVal) { // not seeding at all
                        TransferManager.instance().stopSeedingTorrents();
                        UIUtils.showShortMessage(SettingsActivity.this, R.string.seeding_has_been_turned_off);
                    }

                    if (preferenceSeedingWifiOnly != null) {
                        preferenceSeedingWifiOnly.setEnabled(newVal);
                    }

                    UXStats.instance().log(newVal ? UXAction.SHARING_SEEDING_ENABLED : UXAction.SHARING_SEEDING_DISABLED);
                    return true;
                }
            });
        }

        if (preferenceSeedingWifiOnly != null) {
            preferenceSeedingWifiOnly.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean newVal = (Boolean) newValue;
                    if (newVal && !NetworkManager.instance().isDataWIFIUp()) { // not seeding on mobile data
                        TransferManager.instance().stopSeedingTorrents();
                        UIUtils.showShortMessage(SettingsActivity.this, R.string.wifi_seeding_has_been_turned_off);
                    }
                    return true;
                }
            });
        }

        if (preferenceSeeding != null && preferenceSeedingWifiOnly != null) {
            preferenceSeedingWifiOnly.setEnabled(preferenceSeeding.isChecked());
        }
    }

    private void setupClearIndex() {
        final SimpleActionPreference preference = (SimpleActionPreference) findPreference("frostwire.prefs.internal.clear_index");

        if (preference != null) {
            updateIndexSummary(preference);
            preference.setOnActionListener(new OnClickListener() {
                public void onClick(View v) {
                    LocalSearchEngine.instance().clearCache();
                    UIUtils.showShortMessage(SettingsActivity.this, R.string.deleted_crawl_cache);
                    updateIndexSummary(preference);
                }
            });
        }
    }

    private void setupSearchEngines() {
        PreferenceScreen category = (PreferenceScreen) findPreference(Constants.PREF_KEY_SEARCH_PREFERENCE_CATEGORY);
        if (category != null) {
            for (SearchEngine engine : SearchEngine.getEngines()) {
                CheckBoxPreference preference = (CheckBoxPreference) findPreference(engine.getPreferenceKey());
                if (preference != null) { //it could already have been removed due to remote config value.
                    //LOG.info(engine.getName() + " is enabled: " + engine.isActive());
                    if (!engine.isActive()) {
                        LOG.info("removing preference for engine " + engine.getName());
                        category.removePreference(preference);
                    }
                }
            }
        }
    }

    private void updateIndexSummary(SimpleActionPreference preference) {
        float size = (((float) LocalSearchEngine.instance().getCacheSize()) / 1024) / 1024;
        preference.setSummary(getString(R.string.crawl_cache_size, size));
    }

    private void updateConnectSwitch() {
        SwitchPreference preference = (SwitchPreference) findPreference("frostwire.prefs.internal.connect_disconnect");
        if (preference != null) {
            final OnPreferenceChangeListener onPreferenceChangeListener = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener(null);

            preference.setSummary(R.string.bittorrent_network_summary);
            preference.setEnabled(true);
            if (Engine.instance().isStarted()) {
                preference.setChecked(true);
            } else if (Engine.instance().isStarting() || Engine.instance().isStopping()) {
                connectSwitchImOnIt(preference);
            } else if (Engine.instance().isStopped() || Engine.instance().isDisconnected()) {
                preference.setChecked(false);
            }
            preference.setOnPreferenceChangeListener(onPreferenceChangeListener);
        }
    }

    private void connectSwitchImOnIt(SwitchPreference preference) {
        final OnPreferenceChangeListener onPreferenceChangeListener = preference.getOnPreferenceChangeListener();
        preference.setOnPreferenceChangeListener(null);
        preference.setEnabled(false);
        preference.setSummary(R.string.im_on_it);
        preference.setOnPreferenceChangeListener(onPreferenceChangeListener);
    }

    private void setupConnectSwitch() {
        SwitchPreference preference = (SwitchPreference) findPreference("frostwire.prefs.internal.connect_disconnect");
        if (preference != null) {
            preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean newStatus = ((Boolean) newValue).booleanValue();
                    if (Engine.instance().isStarted() && !newStatus) {
                        disconnect();
                    } else if (newStatus && (Engine.instance().isStopped() || Engine.instance().isDisconnected())) {
                        connect();
                    }
                    return true;
                }
            });
        }
    }

    private void setupUXStatsOption() {
        final CheckBoxPreference checkPref = (CheckBoxPreference) findPreference(Constants.PREF_KEY_UXSTATS_ENABLED);
        if (checkPref != null) {
            checkPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean newVal = (Boolean) newValue;
                    if (!newVal) { // not send ux stats
                        UXStats.instance().setContext(null);
                    }
                    return true;
                }
            });
        }
    }

    private void setupAbout() {
        Preference p = findPreference(Constants.PREF_KEY_SHOW_ABOUT);
        p.setIntent(new Intent(this, AboutActivity.class));
    }

    private void setupStorageOption() {
        // intentional repetition of preference value here
        String kitkatKey = "frostwire.prefs.storage.path";
        String lollipopKey = "frostwire.prefs.storage.path_asf";

        PreferenceCategory category = (PreferenceCategory) findPreference("frostwire.prefs.general");

        if (AndroidPlatform.saf()) {
            // make sure this won't be saved for kitkat
            Preference p = findPreference(kitkatKey);
            if (p != null) {
                category.removePreference(p);
            }
            p = findPreference(lollipopKey);
            if (p != null) {
                p.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        StoragePreference.updateStorageOptionSummary(SettingsActivity.this, newValue.toString());
                        return true;
                    }
                });
                StoragePreference.updateStorageOptionSummary(this, ConfigurationManager.instance().getStoragePath());
            }
        } else {
            Preference p = findPreference(lollipopKey);
            if (p != null) {
                category.removePreference(p);
            }
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
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void connect() {
        final Activity context = this;
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Engine.instance().startServices();

                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        SwitchPreference preference = (SwitchPreference) findPreference("frostwire.prefs.internal.connect_disconnect");
                        connectSwitchImOnIt(preference);
                    }
                });

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                UIUtils.showShortMessage(context, R.string.toast_on_connect);
                updateConnectSwitch();
            }
        };

        task.execute();
    }

    private void disconnect() {
        final Context context = this;
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Engine.instance().stopServices(true);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                UIUtils.showShortMessage(context, R.string.toast_on_disconnect);
                updateConnectSwitch();
            }
        };

        task.execute();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

        boolean r = super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference instanceof PreferenceScreen) {
            initializePreferenceScreen((PreferenceScreen) preference);
            currentPreferenceKey = preference.getKey();
        }
        return r;
    }

    /**
     * HOW TO HIDE A NESTED PreferenceScreen ActionBar icon.
     * The nested PreferenceScreens are basically Dialog instances,
     * if we want to hide the icon on those, we need to get their dialog.getActionBar()
     * instance, hide the icon, and then we need to set the click listeners for the
     * dialog's laid out views. Here we do all that.
     *
     * @param preferenceScreen
     */
    private void initializePreferenceScreen(PreferenceScreen preferenceScreen) {
        final Dialog dialog = preferenceScreen.getDialog();
        if (dialog != null) {
            hideActionBarIcon(dialog.getActionBar());
            View homeButton = dialog.findViewById(android.R.id.home);

            if (homeButton != null) {
                OnClickListener dismissDialogClickListener = new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                    }
                };

                ViewParent homeBtnContainer = homeButton.getParent();
                if (homeBtnContainer instanceof FrameLayout) {
                    ViewGroup containerParent = (ViewGroup) homeBtnContainer.getParent();

                    if (containerParent instanceof LinearLayout) {
                        containerParent.setOnClickListener(dismissDialogClickListener);
                    } else {
                        ((FrameLayout) homeBtnContainer).setOnClickListener(dismissDialogClickListener);
                    }
                } else {
                    homeButton.setOnClickListener(dismissDialogClickListener);
                }
            }
        }
    }
}
