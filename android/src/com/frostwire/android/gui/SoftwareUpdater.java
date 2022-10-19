/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui;

import android.content.Intent;
import android.net.Uri;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.SoftwareUpdaterDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoftwareUpdater {

    private static final Logger LOG = Logger.getLogger(SoftwareUpdater.class);
    private static final boolean ALWAYS_SHOW_UPDATE_DIALOG = false; // debug flag.

    private static final long UPDATE_MESSAGE_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    private static final String UPDATE_ACTION_OTA = "ota";
    private static final String UPDATE_ACTION_MARKET = "market";
    private static final String UPDATE_ACTION_OTA_OVERRIDE = "ota_override";

    private boolean oldVersion;
    private String latestVersion;
    private Update update;
    private long updateTimestamp;

    private SoftwareUpdater() {
        this.oldVersion = false;
        this.latestVersion = Constants.FROSTWIRE_VERSION_STRING;
    }

    private static class Loader {
        static final SoftwareUpdater INSTANCE = new SoftwareUpdater();
    }

    public static SoftwareUpdater getInstance() {
        return Loader.INSTANCE;
    }

    public void checkForUpdate(final MainActivity activity) {
        long now = System.currentTimeMillis();
        if (now - updateTimestamp < UPDATE_MESSAGE_TIMEOUT) {
            return;
        }
        updateTimestamp = now;
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC,
                () -> {
                    final boolean result = SoftwareUpdater.checkUpdateAsyncTask(activity, this);
                    SystemUtils.postToUIThread(() -> SoftwareUpdater.checkUpdateAsyncPost(activity, this, result));
                });
    }

    /**
     * @return true if there's an update available.
     */
    private boolean shouldHandleOTAUpdate() {
        if (UPDATE_ACTION_OTA_OVERRIDE.equals(update.a)) {
            LOG.info("handleOTAUpdate() overriding, take update from OTA message");
            update.a = UPDATE_ACTION_OTA;
        }

        if (oldVersion) {
            if (update.a == null) {
                update.a = UPDATE_ACTION_OTA; // make it the old behavior
            }

            if (update.a.equals(UPDATE_ACTION_OTA)) {
                //Jan/22/2020 - Until we figure out the PackageManager integration
                // we won't download the apk, we'll let the user download it with the
                // web browser.
                return true;
            } else if (update.a.equals(UPDATE_ACTION_MARKET)) {
                return update.m != null;
            }
        }
        return false;
    }

    public void notifyUserAboutUpdate(final MainActivity activity) {
        try {
            if (update.a == null) {
                update.a = UPDATE_ACTION_OTA; // make it the old behavior
            }

            if (update.a.equals(UPDATE_ACTION_OTA)) {
                // Fresh runs with fast connections might send the broadcast intent before
                // MainActivity has had a chance to register the broadcast receiver (onResume)
                // therefore, the menu update icon will only show on the 2nd run only
                activity.updateNavigationMenu(true);
                SoftwareUpdaterDialog dlg = SoftwareUpdaterDialog.newInstance(
                        update.u,
                        update.updateMessages,
                        update.changelog);
                dlg.show(activity.getFragmentManager());
            } else if (update.a.equals(UPDATE_ACTION_MARKET)) {

                String message = StringUtils.getLocaleString(update.marketMessages, activity.getString(R.string.update_message));

                UIUtils.showYesNoDialog(activity.getFragmentManager(), message, R.string.update_title, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(update.m));
                    activity.startActivity(intent);
                });
            }
        } catch (Throwable e) {
            LOG.error("Failed to notify update", e);
            updateTimestamp = -1; // try again next time MainActivity is resumed
        }
    }

    /**
     * mv = my version
     * lv = latest version
     * <p/>
     * returns true if mv is older.
     */
    private boolean isFrostWireOld(byte[] mv, byte[] lv) {
        boolean a = mv[0] < lv[0];
        boolean b = mv[0] == lv[0] && mv[1] < lv[1];
        boolean c = mv[0] == lv[0] && mv[1] == lv[1] && mv[2] < lv[2];
        return a || b || c;
    }

    private boolean isFrostWireOld(String latestBuild) {
        int myBuild = BuildConfig.VERSION_CODE;
        if (Constants.IS_BASIC_AND_DEBUG) {
            myBuild += 10000;
        }
        boolean result;
        try {
            int latestBuildNum = Integer.parseInt(latestBuild);
            result = myBuild < latestBuildNum;
        } catch (Throwable t) {
            LOG.error("isFrostWireOld() can't parse latestBuild number.", t);
            result = false;
        }
        LOG.info("isFrostWireOld(myBuild=" + myBuild + ", latestBuild=" + latestBuild + ") => " + result);
        return result;
    }

    private void updateConfiguration(Update update, MainActivity mainActivity) {
        if (update.config == null) {
            return;
        }

        if (update.config.activeSearchEngines != null) {
            for (String name : update.config.activeSearchEngines.keySet()) {
                SearchEngine engine = SearchEngine.forName(name);
                if (engine != null) {
                    //LOG.info(engine.getName() + " is remotely active: " + update.config.activeSearchEngines.get(name));
                    Boolean engineActive = update.config.activeSearchEngines.get(name);
                    engine.setActive(engineActive != null && engineActive);

                } else {
                    LOG.warn("Can't find any search engine by the name of: '" + name + "'");
                }
            }
        }

        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setStringArray(Constants.PREF_KEY_GUI_OFFERS_WATERFALL, update.config.waterfall);

        CM.setInt(Constants.PREF_KEY_GUI_MOPUB_ALBUM_ART_BANNER_THRESHOLD, update.config.mopubAlbumArtBannerThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_MOPUB_PREVIEW_BANNER_THRESHOLD, update.config.mopubPreviewBannerThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_THRESHOLD, update.config.mopubSearchHeaderBannerThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_DISMISS_INTERVAL_IN_MS, update.config.mopubSearchHeaderBannerIntervalInMs);

        CM.setInt(Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD, update.config.removeAdsB2bThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS, update.config.interstitialOffersTransferStarts);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES, update.config.interstitialTransferOffersTimeoutInMinutes);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MINUTES, update.config.interstitialFirstDisplayDelayInMinutes);

        if (update.config.rewardAdFreeMinutes > Constants.MAX_REWARD_AD_FREE_MINUTES) {
            update.config.rewardAdFreeMinutes = Constants.MIN_REWARD_AD_FREE_MINUTES;
        }
        CM.setInt(Constants.PREF_KEY_GUI_REWARD_AD_FREE_MINUTES, update.config.rewardAdFreeMinutes);

        // This has to be invoked once again here. It gets invoked by main activity on resume before we're done on this thread.
        Offers.initAdNetworks(mainActivity);
    }

    private static byte[] buildVersion() {
        try {
            String[] arr = Constants.FROSTWIRE_VERSION_STRING.split("\\.");
            return new byte[]{Byte.parseByte(arr[0]), Byte.parseByte(arr[1]), Byte.parseByte(arr[2])};
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return new byte[]{0, 0, 0};
    }


    private static class Update {
        /**
         * version X.Y.Z
         */
        public String v;

        /**
         * version code: Plus = 9000000 + manifest:versionCode; Basic = 8000000 + manifest:versionCode
         */
        String vc;

        /**
         * .apk download URL
         */
        public String u;

        /**
         * .apk md5
         */
        public String md5;

        /**
         * Address from the market
         */
        public String m;

        /**
         * Action for the update message
         * - "ota" is download from 'u' (a regular http)
         * - "market" go to market page
         */
        public String a;

        List<String> changelog;

        Map<String, String> updateMessages;
        Map<String, String> marketMessages;
        public Config config;
    }

    @SuppressWarnings("CanBeFinal")
    private static class Config {
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        Map<String, Boolean> activeSearchEngines;
        String[] waterfall;
        int removeAdsB2bThreshold = 50;
        int mopubAlbumArtBannerThreshold = 50;
        int mopubPreviewBannerThreshold = 101;
        int interstitialOffersTransferStarts = 3;
        int interstitialTransferOffersTimeoutInMinutes = 10;
        int interstitialFirstDisplayDelayInMinutes = 3;
        int rewardAdFreeMinutes = Constants.MIN_REWARD_AD_FREE_MINUTES;

        // ux stats
        int mopubSearchHeaderBannerThreshold = 80;
        int mopubSearchHeaderBannerIntervalInMs = 60000; // 1 min
    }

    private static boolean checkUpdateAsyncTask(MainActivity activity,
                                                final SoftwareUpdater softwareUpdater) {
        SystemUtils.ensureBackgroundThreadOrCrash("SoftwareUpdater::checkUpdateAsyncTask");
        try {
            byte[] jsonBytes = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).
                    getBytes(Constants.SERVER_UPDATE_URL, 5000, Constants.USER_AGENT, null);
            if (jsonBytes != null) {
                softwareUpdater.update = JsonUtils.toObject(new String(jsonBytes), Update.class);
                if (softwareUpdater.update.vc != null) {
                    softwareUpdater.oldVersion = softwareUpdater.isFrostWireOld(softwareUpdater.update.vc);
                } else {
                    softwareUpdater.latestVersion = softwareUpdater.update.v;
                    String[] latestVersionArr = softwareUpdater.latestVersion.split("\\.");
                    // lv = latest version
                    byte[] lv = new byte[]{Byte.parseByte(latestVersionArr[0]), Byte.parseByte(latestVersionArr[1]), Byte.parseByte(latestVersionArr[2])};
                    // mv = my version
                    byte[] mv = buildVersion();
                    softwareUpdater.oldVersion = softwareUpdater.isFrostWireOld(mv, lv);
                }
                softwareUpdater.updateConfiguration(softwareUpdater.update, activity);
            } else {
                LOG.warn("Could not fetch update information from " + Constants.SERVER_UPDATE_URL);
            }
            return softwareUpdater.shouldHandleOTAUpdate();
        } catch (Throwable e) {
            LOG.error("Failed to check/retrieve/update the update information", e);
        }
        return false;
    }

    private static void checkUpdateAsyncPost(MainActivity activity,
                                             final SoftwareUpdater softwareUpdater, boolean result) {
        SystemUtils.ensureUIThreadOrCrash("SoftwareUpdater::checkUpdateAsyncPost");
        //nav menu or other components always needs to be updated after we read the config.
        Intent intent = new Intent(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE);
        intent.putExtra("value", result);
        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
        if (ALWAYS_SHOW_UPDATE_DIALOG || result) {
            softwareUpdater.notifyUserAboutUpdate(activity);
        }

        // Even if we're offline, we need to disable these for the Google Play Distro.
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
            SearchEngine.SOUNCLOUD.setActive(false);
        }
    }
}
