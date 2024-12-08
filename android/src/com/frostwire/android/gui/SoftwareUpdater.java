/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.SoftwareUpdaterDialog;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

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

    private boolean oldVersion;
    private Update update;
    private long updateTimestamp;

    private SoftwareUpdater() {
        this.oldVersion = false;
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
        return oldVersion && update != null && update.u != null;
    }

    public void notifyUserAboutUpdate(final MainActivity activity) {
        try {
            activity.updateNavigationMenu(true);
            SoftwareUpdaterDialog dlg = SoftwareUpdaterDialog.newInstance(
                    update.u, //apkDownloadURL
                    update.updateMessages,
                    update.changelog);
            dlg.show(activity.getSupportFragmentManager());
        } catch (Throwable e) {
            LOG.error("Failed to notify update", e);
            updateTimestamp = -1; // try again next time MainActivity is resumed
        }
    }

    private boolean isFrostWireOldByVersionCode(String latestVersionCodeFromUpdateMessage) {
        if (latestVersionCodeFromUpdateMessage == null) {
            LOG.error("isFrostWireOldByVersionCode() latestVersionCodeFromUpdateMessage is null");
            return false;
        }
        // regardless of the prefix, basic or plus, we only care about the last 4 digits
        boolean result;
        try {
            int latestBuildNum = Integer.parseInt(latestVersionCodeFromUpdateMessage) % 10000;
            int myBuild = BuildConfig.VERSION_CODE % 10000;
            result = myBuild < latestBuildNum;
        } catch (Throwable t) {
            LOG.error("isFrostWireOldByVersionCode() can't parse latestVersionCodeFromUpdateMessage number.", t);
            return false;
        }
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

    private static class Update {
        /**
         * version code: Plus = 9090000 + manifest:versionCode; Basic = 9080000 + manifest:versionCode
         */
        String vc;

        /**
         * Download URL
         */
        public String u;

        List<String> changelog;

        Map<String, String> updateMessages;
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
                    softwareUpdater.oldVersion = softwareUpdater.isFrostWireOldByVersionCode(softwareUpdater.update.vc);
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
        // Even if we're offline, we need to disable these for the Google Play Distro.
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
            SearchEngine.SOUNCLOUD.setActive(false);
            SearchEngine.YT.setActive(false);
        }

        //nav menu or other components always needs to be updated after we read the config.
        Intent intent = new Intent(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE);
        intent.putExtra("value", result);
        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
        if (ALWAYS_SHOW_UPDATE_DIALOG || result) {
            softwareUpdater.notifyUserAboutUpdate(activity);
        }
    }
}
