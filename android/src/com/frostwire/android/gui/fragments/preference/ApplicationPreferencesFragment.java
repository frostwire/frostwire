/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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

import android.app.Activity;
import android.app.Dialog;

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
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.text.MessageFormat;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ApplicationPreferencesFragment extends AbstractPreferenceFragment implements AbstractDialog.OnDialogClickListener {

    private static final Logger LOG = Logger.getLogger(ApplicationPreferencesFragment.class);

    private static final String CONFIRM_STOP_HTTP_IN_PROGRESS_DIALOG_TAG = "ApplicationPreferencesFragment.DIALOG.stop.http";

    private static PausedAdsOnPreferenceClickListener pausedAdsPreferenceClickListener;

    // TODO: refactor this
    // due to the separation of fragments and activities
    public static long removeAdsPurchaseTime = 0;

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
        setupStore(removeAdsPurchaseTime);
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
        // intentional repetition of preference value here
        String kitkatKey = "frostwire.prefs.storage.path";
        String lollipopKey = "frostwire.prefs.storage.path_asf";

        PreferenceCategory category = findPreference("frostwire.prefs.general");

        if (AndroidPlatform.saf()) {
            // make sure this won't be saved for kitkat
            Preference p = findPreference(kitkatKey);
            if (p != null) {
                category.removePreference(p);
            }
            p = findPreference(lollipopKey);
            if (p != null) {
                p.setOnPreferenceChangeListener((preference, newValue) -> {
                    updateStorageOptionSummary(newValue.toString());
                    return true;
                });
                updateStorageOptionSummary(ConfigurationManager.instance().getStoragePath());
            }
        } else {
            Preference p = findPreference(lollipopKey);
            if (p != null) {
                category.removePreference(p);
            }
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

    private void updateStorageOptionSummary(String newPath) {
        // intentional repetition of preference value here
        String lollipopKey = "frostwire.prefs.storage.path_asf";
        if (AndroidPlatform.saf()) {
            Preference p = findPreference(lollipopKey);
            if (p != null) {
                p.setSummary(newPath);
            }
        }
    }

    //////////////////////////////
    // AD REMOVAL PREFERENCE LOGIC

    /// ///////////////////////////
    private void setupStore(final long purchaseTimestamp) {
        pausedAdsPreferenceClickListener = new PausedAdsOnPreferenceClickListener(getActivity());
        SetupStoreTaskParamHolder paramHolder = new SetupStoreTaskParamHolder(this, purchaseTimestamp);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            SetupStoreTaskParamHolder resultTaskParamHolder = checkMinutesLeftPausedAsync(paramHolder);
            SystemUtils.postToUIThread(() -> setupStorePostTask(paramHolder, resultTaskParamHolder));
        });
    }

    private static class SetupStoreTaskParamHolder {
        final long purchaseTimestamp;
        int minutesPaused = -1;
        WeakReference<ApplicationPreferencesFragment> appPrefsFragRef;

        SetupStoreTaskParamHolder(ApplicationPreferencesFragment referent, long purchaseTimestamp) {
            appPrefsFragRef = Ref.weak(referent);
            this.purchaseTimestamp = purchaseTimestamp;
        }
    }

    private static SetupStoreTaskParamHolder checkMinutesLeftPausedAsync(SetupStoreTaskParamHolder paramHolder) {
        SystemUtils.ensureBackgroundThreadOrCrash("ApplicationPreferencesFragment::checkMinutesLeftPausedAsync");
        paramHolder.minutesPaused = Offers.getMinutesLeftPausedAsync();
        return paramHolder;
    }

    private static void setupStorePostTask(SetupStoreTaskParamHolder paramHolder,
                                           @SuppressWarnings("unused") SetupStoreTaskParamHolder unusedResultTaskParamHolder) {
        SystemUtils.ensureUIThreadOrCrash("ApplicationPreferencesFragment::setupStorePostTask");
        if (!Ref.alive(paramHolder.appPrefsFragRef)) {
            return;
        }
        ApplicationPreferencesFragment applicationPreferencesFragment = paramHolder.appPrefsFragRef.get();
        if (applicationPreferencesFragment == null) {
            return;
        }
        Activity settingsActivity = applicationPreferencesFragment.getActivity();
        if (settingsActivity == null) {
            return;
        }
        final long purchaseTimestamp = paramHolder.purchaseTimestamp;

        Preference p = applicationPreferencesFragment.findPreference("frostwire.prefs.offers.buy_no_ads");
        if (p == null) {
            return;
        }
        if (Offers.disabledAds() && pausedAdsPreferenceClickListener.adsPaused()) {
            final int minutesPausedLeft = paramHolder.minutesPaused;
            // Paused summary
            String summaryMinutesLeft = minutesPausedLeft > 1 ?
                    applicationPreferencesFragment.getString(R.string.minutes_left_ad_free, minutesPausedLeft) :
                    applicationPreferencesFragment.getString(R.string.minute_left_ad_free);
            p.setSummary(summaryMinutesLeft);
            p.setOnPreferenceClickListener(pausedAdsPreferenceClickListener);
        }
    }

    private static final class DoNothingOnPreferenceClickListener implements Preference.OnPreferenceClickListener {

        @Override
        public boolean onPreferenceClick(@NonNull Preference preference) {
            return true;
        }
    }

    private static final class PausedAdsOnPreferenceClickListener implements Preference.OnPreferenceClickListener {
        private WeakReference<Activity> activityRef;
        private static int rewarded_video_minutes = -1;
        private static long paused_timestamp = -1;
        private static int clicksLeft = 10;

        PausedAdsOnPreferenceClickListener(Activity activity) {
            activityRef = Ref.weak(activity);
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, PausedAdsOnPreferenceClickListener::loadPausedAdsInfoAsync);
        }

        @Override
        public boolean onPreferenceClick(@NonNull Preference preference) {
            // reset reward video timer
            LOG.info("onPreferenceClick() clicks left: " + clicksLeft);
            if (adsPaused() && --clicksLeft <= 0) {
                clicksLeft = 10;
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Offers::unPauseAdsAsync);
                if (Ref.alive(activityRef)) {
                    activityRef.get().finish();
                }
                LOG.info("onPreferenceClick(): ads un-paused");
                return true;
            }
            return true;
        }

        private boolean adsPaused() {
            long pause_duration = rewarded_video_minutes * 60_000L;
            long time_on_pause = System.currentTimeMillis() - paused_timestamp;
            return time_on_pause < pause_duration;
        }

        private static void loadPausedAdsInfoAsync() {
            ConfigurationManager CM = ConfigurationManager.instance();
            rewarded_video_minutes = CM.getInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
            paused_timestamp = CM.getLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP);
        }
    }
    /////////////////////////////////////
    // END OF AD REMOVAL PREFERENCE LOGIC
    /////////////////////////////////////
}
