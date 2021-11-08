/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

import static com.frostwire.android.util.Asyncs.async;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.android.offers.Product;
import com.frostwire.android.offers.Products;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.Collection;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ApplicationPreferencesFragment extends AbstractPreferenceFragment implements AbstractDialog.OnDialogClickListener {

    private static final Logger LOG = Logger.getLogger(ApplicationPreferencesFragment.class);

    private static final boolean INTERNAL_BUILD = BuildConfig.DEBUG;
    private static final int MILLISECONDS_IN_A_DAY = 86400000;
    private static final String CONFIRM_STOP_HTTP_IN_PROGRESS_DIALOG_TAG = "ApplicationPreferencesFragment.DIALOG.stop.http";

    private static DoNothingOnPreferenceClickListener doNothingOnPreferenceClickListener;
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
        setupStore(removeAdsPurchaseTime);
    }

    @Override
    public void onResume() {
        super.onResume();
        initComponents();
    }

    private void setupDataSaving() {
        SwitchPreference preference = findPreference(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
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
    //////////////////////////////
    private void setupStore(final long purchaseTimestamp) {
        pausedAdsPreferenceClickListener = new PausedAdsOnPreferenceClickListener(getActivity());
        SetupStoreTaskParamHolder paramHolder = new SetupStoreTaskParamHolder(this, purchaseTimestamp);
        // Async gymnastics to pass both the purchase timestamp and the amounts of minutes left paused
        // to the UI Thread task, we use a Holder object for this.
        //<T1, R> void async(ResultTask1<T1, R> task,
        //        T1 arg1,
        //        ResultPostTask1<T1, R> post) //result post task just doesn't return anything
        async(ApplicationPreferencesFragment::checkMinutesLeftPausedAsync, paramHolder,
                ApplicationPreferencesFragment::setupStorePostTask);
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
        paramHolder.minutesPaused = Offers.getMinutesLeftPausedAsync();
        return paramHolder;
    }

    private static void setupStorePostTask(SetupStoreTaskParamHolder paramHolder,
                                           @SuppressWarnings("unused") SetupStoreTaskParamHolder unusedResultTaskParamHolder) {
        if (!Ref.alive(paramHolder.appPrefsFragRef)) {
            return;
        }
        ApplicationPreferencesFragment applicationPreferencesFragment = paramHolder.appPrefsFragRef.get();
        Activity settingsActivity = applicationPreferencesFragment.getActivity();
        final long purchaseTimestamp = paramHolder.purchaseTimestamp;

        Preference p = applicationPreferencesFragment.findPreference("frostwire.prefs.offers.buy_no_ads");
        if (p != null && Offers.disabledAds() && pausedAdsPreferenceClickListener.adsPaused()) {
            final int minutesPausedLeft = paramHolder.minutesPaused;
            // Paused summary
            String summaryMinutesLeft = minutesPausedLeft > 1 ?
                    applicationPreferencesFragment.getString(R.string.minutes_left_ad_free, minutesPausedLeft) :
                    applicationPreferencesFragment.getString(R.string.minute_left_ad_free);
            p.setSummary(summaryMinutesLeft);
            p.setOnPreferenceClickListener(pausedAdsPreferenceClickListener);
        } else if (p != null && PlayStore.available() && (Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG)) {
            PlayStore playStore = PlayStore.getInstance(settingsActivity);
            playStore.refresh();
            Collection<Product> purchasedProducts = Products.listEnabled(playStore, Products.DISABLE_ADS_FEATURE);
            if (purchaseTimestamp == 0 && purchasedProducts.size() > 0) {
                // HOW MUCH TIME LEFT OR SUBSCRIPTION PLAN SUMMARY
                applicationPreferencesFragment.initRemoveAdsSummaryWithPurchaseInfo(p, purchasedProducts);
                //otherwise, a BuyActivity intent has been configured on application_preferences.xml
            } else if (purchaseTimestamp > 0 &&
                    (System.currentTimeMillis() - purchaseTimestamp) < 30000) {
                // STILL PROCESSING SUMMARY
                p.setSummary(applicationPreferencesFragment.getString(R.string.processing_payment) + "...");
                p.setOnPreferenceClickListener(doNothingOnPreferenceClickListener);
            } else {
                // ENCOURAGE AD-REMOVAL ACTION SUMMARY
                p.setSummary(R.string.remove_ads_description);
                p.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(settingsActivity, BuyActivity.class);
                    applicationPreferencesFragment.startActivityForResult(intent, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
                    return true;
                });
            }
        }
    }

    private void initRemoveAdsSummaryWithPurchaseInfo(Preference p, Collection<Product> purchasedProducts) {
        if (purchasedProducts != null && purchasedProducts.size() > 0) {
            Product product = purchasedProducts.iterator().next();
            String daysLeft = "";
            // if it's a one time purchase, show user how many days left she has.
            if (!product.subscription() && product.purchased()) {
                int daysBought = Products.toDays(product.sku());
                if (daysBought > 0) {
                    long timePassed = System.currentTimeMillis() - product.purchaseTime();
                    int daysPassed = (int) timePassed / MILLISECONDS_IN_A_DAY;
                    if (daysPassed > 0 && daysPassed < daysBought) {
                        daysLeft = " (" + getString(R.string.days_left) + ": " + (daysBought - daysPassed) + ")";
                    }
                }
            }
            p.setSummary(getString(R.string.current_plan) + ": " + product.description() + daysLeft);
        }
        p.setOnPreferenceClickListener(new RemoveAdsOnPreferenceClickListener(getActivity(), purchasedProducts));
    }


    private static final class DoNothingOnPreferenceClickListener implements Preference.OnPreferenceClickListener {

        @Override
        public boolean onPreferenceClick(Preference preference) {
            return true;
        }
    }

    private static final class RemoveAdsOnPreferenceClickListener implements Preference.OnPreferenceClickListener {

        private int clicksLeftToConsumeProducts = 20;
        private final Collection<Product> purchasedProducts;
        private final WeakReference<Activity> activityRef;

        RemoveAdsOnPreferenceClickListener(Activity activity, final Collection<Product> purchasedProducts) {
            activityRef = Ref.weak(activity);
            this.purchasedProducts = purchasedProducts;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            final boolean gotProducts = purchasedProducts != null && !purchasedProducts.isEmpty();
            if (gotProducts) {
                if (gotProducts) {
                    LOG.info("onPreferenceClick(): Products purchased by user:");
                    for (Product p : purchasedProducts) {
                        LOG.info(" - " + p.description() + " (" + p.sku() + ")");
                    }
                }

                if (INTERNAL_BUILD) {
                    clicksLeftToConsumeProducts--;
                    LOG.info("onPreferenceClick(): If you click again " + clicksLeftToConsumeProducts + " times, all ONE-TIME purchases or Paused Ads Time left will be forced-consumed.");
                    if ((0 >= clicksLeftToConsumeProducts && clicksLeftToConsumeProducts < 11)) {
                        if (clicksLeftToConsumeProducts == 0) {
                            if (gotProducts) {
                                for (Product p : purchasedProducts) {
                                    if (p.subscription()) {
                                        continue;
                                    }
                                    PlayStore.getInstance(activityRef.get()).consume(p);
                                    LOG.info("onPreferenceClick() - " + p.description() + " (" + p.sku() + ") force-consumed!");
                                    UIUtils.showToastMessage(preference.getContext(),
                                            "Product " + p.sku() + " forced-consumed.",
                                            Toast.LENGTH_SHORT);
                                }
                                if (Ref.alive(activityRef)) {
                                    activityRef.get().finish();
                                }
                            }
                        }
                    }
                }
                return true; // true = click was handled.
            } else {
                LOG.info("Couldn't find any purchases.");
            }
            return false;
        }
    }

    private static final class PausedAdsOnPreferenceClickListener implements Preference.OnPreferenceClickListener {
        private WeakReference<Activity> activityRef;
        private static int rewarded_video_minutes = -1;
        private static long paused_timestamp = -1;
        private static int clicksLeft = 10;

        PausedAdsOnPreferenceClickListener(Activity activity) {
            activityRef = Ref.weak(activity);
            async(PausedAdsOnPreferenceClickListener::loadPausedAdsInfoAsync);
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            // reset reward video timer
            LOG.info("onPreferenceClick() clicks left: " + clicksLeft);
            if (adsPaused() && --clicksLeft <= 0) {
                clicksLeft = 10;
                async(Offers::unPauseAdsAsync);
                if (Ref.alive(activityRef)) {
                    activityRef.get().finish();
                }
                LOG.info("onPreferenceClick(): ads un-paused");
                return true;
            }
            return true;
        }

        private boolean adsPaused() {
            long pause_duration = rewarded_video_minutes * 60_000;
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
