/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Grzesiek Rzaca (grzesiekrzaca)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.fragments.preference;

import androidx.fragment.app.DialogFragment;

import androidx.preference.SwitchPreference;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.CustomSeekBarPreference;
import com.frostwire.android.gui.views.preference.CustomSeekBarPreference.CustomSeekBarPreferenceDialog;
import com.frostwire.android.gui.views.preference.PortRangePreference;
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
        SwitchPreference prefEnableDHT = findPreference(Constants.PREF_KEY_NETWORK_ENABLE_DHT);
        if (prefEnableDHT != null) {
            prefEnableDHT.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean newStatus = (boolean) newValue;
                if (newStatus) {
                    BTEngine.getInstance().startDht();
                } else {
                    BTEngine.getInstance().stopDht();
                }
                return true;
            });
        }

        SwitchPreference prefSequentialTransfers = findPreference(Constants.PREF_KEY_TORRENT_SEQUENTIAL_TRANSFERS_ENABLED);
        if (prefSequentialTransfers != null) {
            prefSequentialTransfers.setOnPreferenceChangeListener((preference, newValue) -> {
                UIBittorrentDownload.SEQUENTIAL_DOWNLOADS = (boolean) newValue;
                return true;
            });
        }

        SwitchPreference prefIPFilterEnabled = findPreference(Constants.PREF_KEY_TORRENT_IP_FILTER_ENABLED);
        if (prefIPFilterEnabled != null) {
            prefIPFilterEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (boolean) newValue;
                // Handle IP filter enable/disable on background thread
                com.frostwire.android.util.SystemUtils.postToHandler(
                    com.frostwire.android.util.SystemUtils.HandlerThreadName.CONFIG_MANAGER,
                    () -> onIPFilterEnabledChanged(enabled)
                );
                return true;
            });
        }

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
        } else if (preference instanceof PortRangePreference) {
            DialogFragment fragment;
            fragment = PortRangePreference.PortRangePreferenceDialog.newInstance(preference.getKey());
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

    /**
     * Handle IP filter enabled/disabled setting change.
     * Note: For Android, we currently only support enabling/disabling the feature.
     * The actual IP filter rules would need to be loaded from files or managed through
     * a dedicated IP filter management interface.
     */
    private void onIPFilterEnabledChanged(boolean enabled) {
        com.frostwire.android.util.SystemUtils.ensureBackgroundThreadOrCrash("TorrentPreferenceFragment.onIPFilterEnabledChanged");
        
        final BTEngine engine = BTEngine.getInstance();
        if (engine == null) {
            return;
        }

        if (enabled) {
            // For now, we just log that IP filtering would be enabled
            // In a full implementation, this would load and apply IP filter rules
            com.frostwire.util.Logger.getLogger(getClass()).info("IP filtering enabled on Android - feature ready for IP filter rules");
            
            // If there were stored IP filter rules, they would be applied here
            // For now, create an empty filter to demonstrate the functionality
            java.util.List<IPRange> emptyRanges = new java.util.ArrayList<>();
            engine.applyIPFilter(emptyRanges);
        } else {
            engine.clearIPFilter();
            com.frostwire.util.Logger.getLogger(getClass()).info("IP filtering disabled on Android");
        }
    }

    /**
     * Simple IP range class for Android implementation.
     * This is a minimal implementation for demonstration purposes.
     */
    private static class IPRange implements com.frostwire.bittorrent.IPRange {
        private final String description;
        private final String startAddress;
        private final String endAddress;

        public IPRange(String description, String startAddress, String endAddress) {
            this.description = description;
            this.startAddress = startAddress;
            this.endAddress = endAddress;
        }

        public String description() {
            return description;
        }

        public String startAddress() {
            return startAddress;
        }

        public String endAddress() {
            return endAddress;
        }
    }
}
