/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Grzesiek Rzaca (grzesiekrzaca)
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

package com.frostwire.android.gui.fragments.preference;

import android.app.DialogFragment;
import androidx.preference.SwitchPreference;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.CustomSeekBarPreference;
import com.frostwire.android.gui.views.preference.CustomSeekBarPreference.CustomSeekBarPreferenceDialog;
import com.frostwire.bittorrent.BTEngine;

public final class TorrentPreferenceFragment extends AbstractPreferenceFragment {

    public TorrentPreferenceFragment() {
        super(R.xml.settings_torrent);
    }

    @Override
    protected void initComponents() {
        setupTorrentOptions();
        setupSeedingOptions();
    }

    private void setupSeedingOptions() {
        final CheckBoxPreference preferenceSeeding = findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);
        final CheckBoxPreference preferenceSeedingWifiOnly = findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);

        if (preferenceSeeding != null) {
            preferenceSeeding.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean newVal = (Boolean) newValue;

                if (!newVal) { // not seeding at all
                    TransferManager.instance().stopSeedingTorrents();
                    UIUtils.showShortMessage(getView(), R.string.seeding_has_been_turned_off);
                }

                if (preferenceSeedingWifiOnly != null) {
                    preferenceSeedingWifiOnly.setEnabled(newVal);
                }
                return true;
            });
        }

        if (preferenceSeedingWifiOnly != null) {
            preferenceSeedingWifiOnly.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean newVal = (Boolean) newValue;
                if (newVal && !NetworkManager.instance().isDataWIFIUp()) { // not seeding on mobile data
                    TransferManager.instance().stopSeedingTorrents();
                    UIUtils.showShortMessage(getView(), R.string.wifi_seeding_has_been_turned_off);
                }
                return true;
            });
        }

        if (preferenceSeeding != null && preferenceSeedingWifiOnly != null) {
            preferenceSeedingWifiOnly.setEnabled(preferenceSeeding.isChecked());
        }
    }

    private void setupTorrentOptions() {
        SwitchPreference pref = findPreference(Constants.PREF_KEY_NETWORK_ENABLE_DHT);
        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newStatus = (boolean) newValue;
            if (newStatus) {
                BTEngine.getInstance().startDht();
            } else {
                BTEngine.getInstance().stopDht();
            }
            return true;
        });

        final BTEngine e = BTEngine.getInstance();
        setupFWSeekbarPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED, e);
        setupFWSeekbarPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED, e);
        setupFWSeekbarPreference(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, e);
        setupFWSeekbarPreference(Constants.PREF_KEY_TORRENT_MAX_UPLOADS, e);
        setupFWSeekbarPreference(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS, e);
        setupFWSeekbarPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS, e);
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof CustomSeekBarPreference) {
            DialogFragment fragment;
            fragment = CustomSeekBarPreferenceDialog.newInstance((CustomSeekBarPreference) preference);
            fragment.setTargetFragment(this, 0);
            fragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private void setupFWSeekbarPreference(final String key, final BTEngine btEngine) {
        final CustomSeekBarPreference pickerPreference = findPreference(key);
        if (pickerPreference != null) {
            pickerPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if (btEngine != null) {
                    int newVal = (int) newValue;
                    executeBTEngineAction(key, btEngine, newVal);
                    return checkBTEngineActionResult(key, btEngine, newVal);
                }
                return false;
            });
        }
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
}
