/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.ThemeManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.SaveLocationPreference;
import com.frostwire.android.offers.SupportOffer;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import java.text.MessageFormat;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ApplicationPreferencesFragment extends AbstractPreferenceFragment implements AbstractDialog.OnDialogClickListener {

    private static final Logger LOG = Logger.getLogger(ApplicationPreferencesFragment.class);

    private static final String CONFIRM_STOP_HTTP_IN_PROGRESS_DIALOG_TAG = "ApplicationPreferencesFragment.DIALOG.stop.http";

    public ApplicationPreferencesFragment() {
        super(R.xml.settings_application);
    }

    @Override
    protected void initComponents() {
        setupConnectSwitch();
        setupVPNRequirementOption();
        setupStorageOption();
        setupDataSaving();
        setupTheme();
        setupSupportPreference();
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeManager.loadSavedThemeModeAsync(themeMode -> {
            LOG.info("ApplicationPreferencesFragment: onResume()->onThemeLoaded(): themeMode=" + themeMode);
            initComponents();
        });

    }

    private void setupDataSaving() {
        SwitchPreference preference = findPreference(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
        if (preference == null) {
            LOG.error(MessageFormat.format("ApplicationPreferencesFragment.setupDataSaving(): SwitchPreference under key {0} not found", Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY));
            return;
        }
        preference.setOnPreferenceChangeListener((preference1, newValue) -> {
            boolean newVal = (Boolean) newValue;
            if (newVal && !NetworkManager.instance().isDataWIFIUp()) {
                if (TransferManager.instance().isHttpDownloadInProgress()) {
                    YesNoDialog dlg = YesNoDialog.newInstance(
                            CONFIRM_STOP_HTTP_IN_PROGRESS_DIALOG_TAG,
                            R.string.data_saving_kill_http_warning_title,
                            R.string.data_saving_kill_http_warning,
                            YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK
                    );
                    dlg.setTargetFragment(ApplicationPreferencesFragment.this, 0);
                    dlg.show(getFragmentManager(), CONFIRM_STOP_HTTP_IN_PROGRESS_DIALOG_TAG);

                    return false;
                }
                turnOffTransfers();
            }
            return true;
        });
        LOG.info("ApplicationPreferencesFragment.setupDataSaving(): preference change listener set");
    }

    public void setupTheme() {
        ListPreference listPreference = findPreference(Constants.PREF_KEY_GUI_THEME_MODE);
        if (listPreference == null) {
            LOG.error(MessageFormat.format("ApplicationPreferencesFragment.setupTheme(): ListPreference under key {0} not found", Constants.PREF_KEY_GUI_THEME_MODE ));
            return;
        }
        listPreference.setOnPreferenceChangeListener((listPreferenceCalled, newThemeModeStringValue) -> {
            ThemeManager.saveThemeModeAsync((String) newThemeModeStringValue);
            ThemeManager.applyThemeMode((String) newThemeModeStringValue);
            listPreferenceCalled.setSummary(listPreference.getEntries()[listPreference.findIndexOfValue((String) newThemeModeStringValue)]);
            return true;
        });
        LOG.info("ApplicationPreferencesFragment.setupTheme(): about to setupThemeAsync()");
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> loadTheme(listPreference));
    }

    /**
     * Load the last saved theme mode in a background thread, when done update the Preference List with the loaded value
     */
    private void loadTheme(ListPreference listPreference) {
        ThemeManager.loadSavedThemeModeAsync(themeMode -> {
            LOG.info("loadTheme()->onThemeLoaded(): themeMode=" + themeMode);
            String themeModeStringValue = ThemeManager.getThemeEntryFromMode(themeMode);
            String themeModeStringLabel = listPreference.getEntries()[listPreference.findIndexOfValue(themeModeStringValue)].toString();
            listPreference.setValue(themeModeStringValue);
            listPreference.setSummary(themeModeStringLabel);
        });
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (CONFIRM_STOP_HTTP_IN_PROGRESS_DIALOG_TAG.equals(tag) && Dialog.BUTTON_POSITIVE == which) {
            turnOffTransfers();
            setChecked(findPreference(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY), true, false);
        }
    }

    private void turnOffTransfers() {
        TransferManager.instance().stopHttpTransfers();
        TransferManager.instance().pauseTorrents();
        UIUtils.showShortMessage(getView(), R.string.data_saving_turn_off_transfers);
    }

    private void setupVPNRequirementOption() {
        SwitchPreference preference = findPreference(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY);
        if (preference == null) {
            LOG.error(MessageFormat.format("ApplicationPreferencesFragment::setupVPNRequirementOption() Preference with key {0} not found.", Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY));
            return; // Safeguard to prevent crashes
        }
        preference.setOnPreferenceChangeListener((preference1, newValue) -> {
            boolean newVal = (boolean) newValue;
            if (newVal && !NetworkManager.instance().isTunnelUp()) {
                disconnect();
                setChecked(findPreference("frostwire.prefs.internal.connect_disconnect"), false, false);
                UIUtils.showShortMessage(getView(), R.string.switch_off_engine_without_vpn);
            }
            return true;
        });
    }

    private void setupConnectSwitch() {
        SwitchPreference preference = findPreference("frostwire.prefs.internal.connect_disconnect");
        if (preference == null) {
            LOG.error("ApplicationPreferencesFragment::setupConnectSwitch() Preference with key 'frostwire.prefs.internal.connect_disconnect' not found.");
            return; // Safeguard to prevent crashes
        }

        preference.setOnPreferenceChangeListener((preference1, newValue) -> {
            boolean newStatus = (boolean) newValue;
            Engine e = Engine.instance();
            if (e.isStarted() && !newStatus) {
                disconnect();
                UIUtils.showShortMessage(getView(), R.string.toast_on_disconnect);
            } else if (newStatus && (e.isStopped() || e.isDisconnected())) {
                NetworkManager networkManager = NetworkManager.instance();
                if (getPreferenceManager().getSharedPreferences().getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY, false) &&
                        !networkManager.isTunnelUp()) {
                    UIUtils.showShortMessage(getView(), R.string.cannot_start_engine_without_vpn);
                    return false;
                } else if (getPreferenceManager().getSharedPreferences().getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY, false) &&
                        networkManager.isDataMobileUp()) {
                    UIUtils.showShortMessage(getView(), R.string.wifi_network_unavailable);
                    return false;
                } else {
                    connect();
                }
            }
            return true;
        });

        updateConnectSwitchStatus();
    }

    private void setupStorageOption() {
        SaveLocationPreference pref = findPreference("frostwire.prefs.storage.path");
        if (pref != null) {
            pref.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(intent, SaveLocationPreference.REQUEST_CODE);
                return true;
            });
        }
    }

    private void updateConnectSwitchStatus() {
        SwitchPreference preference = findPreference("frostwire.prefs.internal.connect_disconnect");
        Engine e = Engine.instance();
        if (e.isStarted()) {
            setChecked(preference, true, false);
        } else if (e.isStopped() || e.isDisconnected()) {
            setChecked(preference, false, false);
        }
    }

    private void connect() {
        Engine.instance().startServices(); // internally this is an async call in libtorrent
        updateConnectSwitchStatus();
    }

    private void disconnect() {
        Engine.instance().stopServices(true); // internally this is an async call in libtorrent
        updateConnectSwitchStatus();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        LOG.info("onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode + ", data=" + (data != null ? "present" : "null"));
        if (requestCode == SaveLocationPreference.REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            LOG.info("onActivityResult: uri=" + uri);
            String path = extractPathFromUri(uri);
            LOG.info("onActivityResult: extracted path=" + path);
            if (path != null) {
                SaveLocationPreference pref = findPreference("frostwire.prefs.storage.path");
                if (pref instanceof SaveLocationPreference) {
                    LOG.info("onActivityResult: calling onFolderSelected with path=" + path);
                    ((SaveLocationPreference) pref).onFolderSelected(path);
                } else {
                    LOG.warn("onActivityResult: preference not found or wrong type");
                }
            } else {
                LOG.warn("onActivityResult: path extraction returned null");
            }
        } else {
            LOG.warn("onActivityResult: REQUEST_CODE mismatch or result not OK or data null");
        }
    }

    private String extractPathFromUri(Uri uri) {
        if (uri == null) {
            LOG.warn("extractPathFromUri: uri is null");
            return null;
        }
        LOG.info("extractPathFromUri: uri=" + uri);
        // For SAF URIs like "content://com.android.externalstorage.documents/tree/primary:Downloads%2FFrostWire"
        // Extract path and build full filesystem path
        String uriPath = uri.getPath();
        LOG.info("extractPathFromUri: uriPath=" + uriPath);
        if (uriPath != null && uriPath.contains("primary:")) {
            try {
                String relativePath = uriPath.substring(uriPath.indexOf("primary:") + 8);
                relativePath = java.net.URLDecoder.decode(relativePath, "UTF-8");
                LOG.info("extractPathFromUri: extracted relativePath=" + relativePath);

                // The relativePath may be "Download/SubFolder", "Downloads/SubFolder", or just ""
                // Handle both "Download" and "Downloads" (varies by device/Android version)
                String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
                String fullPath;

                if (relativePath.isEmpty() || relativePath.equals("/")) {
                    // User selected the primary storage root, use default Downloads/FrostWire
                    fullPath = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/FrostWire";
                } else if (relativePath.startsWith("Download") || relativePath.startsWith("Downloads")) {
                    // Path already includes Download(s), combine directly with storage root
                    fullPath = storageRoot + "/" + relativePath;
                } else {
                    // Path doesn't include Download(s), append to Downloads directory
                    fullPath = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/" + relativePath;
                }
                LOG.info("extractPathFromUri: storageRoot=" + storageRoot + ", relativePath=" + relativePath + ", fullPath=" + fullPath);
                return fullPath;
            } catch (Exception e) {
                LOG.warn("Could not extract path from URI: " + uri, e);
            }
        } else {
            LOG.warn("extractPathFromUri: uriPath doesn't contain 'primary:', returning null");
        }
        return null;
    }

    private void setupSupportPreference() {
        Preference preference = findPreference("frostwire.prefs.offers.buy_no_ads");
        if (preference == null) {
            return;
        }
        SupportOffer offer = SupportOffer.random();
        preference.setTitle(offer.titleRes);
        preference.setSummary(offer.messageRes);
        preference.setOnPreferenceClickListener(pref -> {
            Activity activity = getActivity();
            if (activity != null) {
                offer.open(activity);
            }
            return true;
        });
    }
}
