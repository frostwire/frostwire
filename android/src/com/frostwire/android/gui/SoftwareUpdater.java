/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.VPNStatusDetailActivity;
import com.frostwire.android.gui.dialogs.SoftwareUpdaterDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.platform.Platforms;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.UXStats;
import com.frostwire.uxstats.UXStatsConf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoftwareUpdater {

    private static final Logger LOG = Logger.getLogger(SoftwareUpdater.class);
    private static final boolean ALWAYS_SHOW_UPDATE_DIALOG = false; // debug flag.

    private static final long UPDATE_MESSAGE_TIMEOUT = 30 * 60 * 1000; // 30 minutes

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

    public void checkForUpdate(final Context context) {
        long now = System.currentTimeMillis();
        if (now - updateTimestamp < UPDATE_MESSAGE_TIMEOUT) {
            return;
        }
        updateTimestamp = now;
        AsyncTask<Void, Void, Boolean> updateTask = new CheckUpdateAsyncTask(this, context);
        updateTask.execute();
    }

    /**
     * @return true if there's an update available.
     */
    private boolean handleOTAUpdate() throws IOException {
        if (update.a != null && !UPDATE_ACTION_OTA_OVERRIDE.equals(update.a)) {
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
                LOG.info("handleOTAUpdate(): it's Google Play, aborting -> false");
                return false;
            }
        }

        if (UPDATE_ACTION_OTA_OVERRIDE.equals(update.a)) {
            LOG.info("handleOTAUpdate() overriding, take update from OTA message");
            update.a = UPDATE_ACTION_OTA;
        }

        if (oldVersion) {
            if (update.a == null) {
                update.a = UPDATE_ACTION_OTA; // make it the old behavior
            }

            if (update.a.equals(UPDATE_ACTION_OTA)) {
                // did we download the newest already?
                if (downloadedLatestFrostWire(update.md5)) {
                    LOG.info("handleOTAUpdate(): downloadedLatestFrostWire(" + update.md5 + ") -> true");
                    return true;
                }
                // didn't download it? go get it now
                else {
                    File apkDirectory = getUpdateApk().getParentFile();
                    if (!apkDirectory.exists()) {
                        apkDirectory.mkdirs();
                    }

                    LOG.info("handleOTAUpdate(): Downloading update... (" + update.md5 + ")");
                    HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).save(update.u, getUpdateApk());
                    LOG.info("handleOTAUpdate(): Finished downloading update... (" + update.md5 + ")");
                    if (downloadedLatestFrostWire(update.md5)) {
                        LOG.info("handleOTAUpdate(): downloadedLatestFrostWire(" + update.md5 + ") -> true");
                        return true;
                    }
                    LOG.info("handleOTAUpdate(): downloadedLatestFrostWire(" + update.md5 + ") -> false");
                }
            } else if (update.a.equals(UPDATE_ACTION_MARKET)) {
                return update.m != null;
            }
        }
        return false;
    }

    private static File getUpdateApk() {
        return Platforms.get().systemPaths().update();
    }

    public void notifyUserAboutUpdate(final Context context) {
        try {
            if (update.a == null) {
                update.a = UPDATE_ACTION_OTA; // make it the old behavior
            }

            if (update.a.equals(UPDATE_ACTION_OTA)) {
                if (!ALWAYS_SHOW_UPDATE_DIALOG && !getUpdateApk().exists()) {
                    LOG.info("notifyUserAboutUpdate(): " + getUpdateApk().getAbsolutePath() + " not found. Aborting.");
                    return;
                }

               // Fresh runs with fast connections might send the broadcast intent before
               // MainActivity has had a chance to register the broadcast receiver (onResume)
               // therefore, the menu update icon will only show on the 2nd run only
               ((MainActivity) context).updateNavigationMenu(true);

                SoftwareUpdaterDialog dlg = SoftwareUpdaterDialog.newInstance(update.updateMessages, update.changelog);
                dlg.show(((Activity) context).getFragmentManager());
            } else if (update.a.equals(UPDATE_ACTION_MARKET)) {

                String message = StringUtils.getLocaleString(update.marketMessages, context.getString(R.string.update_message));

                UIUtils.showYesNoDialog(context, message, R.string.update_title, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(update.m));
                    context.startActivity(intent);
                });
            }
        } catch (Throwable e) {
            LOG.error("Failed to notify update", e);
            updateTimestamp = -1; // try again next time MainActivity is resumed
        }
    }

    /**
     * @param md5 - Expected MD5 hash as a string.
     * @return true if the latest apk was downloaded and md5 verified.
     */
    private boolean downloadedLatestFrostWire(String md5) {
        return getUpdateApk().exists() && checkMD5(getUpdateApk(), md5);
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

    private boolean isFrostWireOld(int myBuild, String latestBuild) {
        if (Constants.IS_BASIC_AND_DEBUG) {
            myBuild += 10000;
        }
        boolean result;
        try {
            int latestBuildNum = Integer.parseInt(latestBuild);
            result = myBuild < latestBuildNum;
        } catch (Throwable ignored) {
            LOG.error("isFrostWireOld() can't parse latestBuild number.", ignored);
            result = false;
        }
        LOG.info("isFrostWireOld(myBuild=" + myBuild + ", latestBuild=" + latestBuild + ") => " + result);
        return result;
    }

    private static String getMD5(File f) {
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");

            // We read the file in buffers so we don't
            // eat all the memory in case we have a huge plugin.
            byte[] buf = new byte[65536];
            int num_read;

            InputStream in = new BufferedInputStream(new FileInputStream(f));

            while ((num_read = in.read(buf)) != -1) {
                m.update(buf, 0, num_read);
            }

            in.close();

            String result = new BigInteger(1, m.digest()).toString(16);

            // pad with zeros if until it's 32 chars long.
            if (result.length() < 32) {
                int paddingSize = 32 - result.length();
                for (int i = 0; i < paddingSize; i++) {
                    result = "0" + result;
                }
            }

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean checkMD5(File f, String expectedMD5) {
        if (expectedMD5 == null) {
            return false;
        }

        if (expectedMD5.length() != 32) {
            return false;
        }

        String checkedMD5 = getMD5(f);
        LOG.info("checkMD5(expected=" + expectedMD5 + ", checked=" + checkedMD5 + ")");
        return checkedMD5 != null && checkedMD5.trim().equalsIgnoreCase(expectedMD5.trim());
    }

    private void updateConfiguration(Update update, Context activityContext) {
        if (update.config == null) {
            return;
        }

        if (update.config.activeSearchEngines != null && update.config.activeSearchEngines.keySet() != null) {
            for (String name : update.config.activeSearchEngines.keySet()) {
                SearchEngine engine = SearchEngine.forName(name);
                if (engine != null) {
                    //LOG.info(engine.getName() + " is remotely active: " + update.config.activeSearchEngines.get(name));
                    engine.setActive(update.config.activeSearchEngines.get(name));
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

        CM.setInt(Constants.PREF_KEY_GUI_OGURY_THRESHOLD, update.config.oguryThreshold);
        CM.setBoolean(Constants.PREF_KEY_GUI_OGURY_KILL_ON_EXIT, update.config.oguryKillOnExit);

        CM.setInt(Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD, update.config.removeAdsB2bThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS, update.config.interstitialOffersTransferStarts);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES, update.config.interstitialTransferOffersTimeoutInMinutes);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_ON_RESUME_FIRST_DISPLAY_DELAY_IN_MINUTES, update.config.interstitialOnResumeFirstDisplayDelayInMinutes);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_ON_RESUME_TIMEOUT_IN_MINUTES, update.config.interstitialOnResumeTimeoutInMinutes);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_ON_EXIT_THRESHOLD, update.config.onExitThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_ON_BACK_THRESHOLD, update.config.onBackThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_ON_RESUME_THRESHOLD, update.config.onResumeThreshold);
        VPNStatusDetailActivity.updateVPNOffer(update.config.vpnOffer);

        // This has to be invoked once again here. It gets invoked by main activity on resume before we're done on this thread.
        Offers.initAdNetworks((Activity) activityContext);

        if (update.config.uxEnabled && ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_UXSTATS_ENABLED)) {
            String url = "http://ux.frostwire.com/aux";
            String os = SearchEngine.getOSVersionString();
            String fwversion = Constants.FROSTWIRE_VERSION_STRING;
            String fwbuild = Constants.FROSTWIRE_BUILD;
            int period = update.config.uxPeriod;
            int minEntries = update.config.uxMinEntries;
            int maxEntries = update.config.uxMaxEntries;

            UXStatsConf uxStatsContext = new UXStatsConf(url, os, fwversion, fwbuild, period, minEntries, maxEntries);
            UXStats.instance().setContext(uxStatsContext);
        }
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
        Map<String, Boolean> activeSearchEngines;
        String[] waterfall;
        int removeAdsB2bThreshold = 50;
        int mopubAlbumArtBannerThreshold = 40;
        int mopubPreviewBannerThreshold = 40;
        int oguryThreshold = 10;
        boolean oguryKillOnExit = false;
        int interstitialOffersTransferStarts = 5;
        int interstitialTransferOffersTimeoutInMinutes = 15;
        int interstitialOnResumeFirstDisplayDelayInMinutes = 30;
        int interstitialOnResumeTimeoutInMinutes = 15;
        int onExitThreshold = 100;
        int onBackThreshold = 100;
        int onResumeThreshold = 100;
        String vpnOffer = VPNStatusDetailActivity.VPNCompanyInfo.ExpressVPN.name();

        // ux stats
        boolean uxEnabled = false;
        int uxPeriod = 3600;
        int uxMinEntries = 10;
        int uxMaxEntries = 10000;
        int mopubSearchHeaderBannerThreshold = 80;
        int mopubSearchHeaderBannerIntervalInMs = 300000; // 5 mins
    }

    private final static class CheckUpdateAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private final SoftwareUpdater softwareUpdater;
        private final WeakReference<Context> contextReference;

        CheckUpdateAsyncTask(SoftwareUpdater softwareUpdater, Context context) {
            this.softwareUpdater = softwareUpdater;
            contextReference = Ref.weak(context);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                byte[] jsonBytes = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).
                        getBytes(Constants.SERVER_UPDATE_URL, 5000, Constants.USER_AGENT, null);

                if (jsonBytes != null) {
                    softwareUpdater.update = JsonUtils.toObject(new String(jsonBytes), Update.class);

                    if (softwareUpdater.update.vc != null) {
                        softwareUpdater.oldVersion = softwareUpdater.isFrostWireOld(BuildConfig.VERSION_CODE, softwareUpdater.update.vc);
                    } else {
                        softwareUpdater.latestVersion = softwareUpdater.update.v;
                        String[] latestVersionArr = softwareUpdater.latestVersion.split("\\.");

                        // lv = latest version
                        byte[] lv = new byte[]{Byte.valueOf(latestVersionArr[0]), Byte.valueOf(latestVersionArr[1]), Byte.valueOf(latestVersionArr[2])};

                        // mv = my version
                        byte[] mv = buildVersion(Constants.FROSTWIRE_VERSION_STRING);

                        softwareUpdater.oldVersion = softwareUpdater.isFrostWireOld(mv, lv);
                    }

                    if (Ref.alive(contextReference)) {
                        softwareUpdater.updateConfiguration(softwareUpdater.update, contextReference.get());
                    }
                } else {
                    LOG.warn("Could not fetch update information from " + Constants.SERVER_UPDATE_URL);
                }

                return softwareUpdater.handleOTAUpdate();
            } catch (Throwable e) {
                LOG.error("Failed to check/retrieve/update the update information", e);
            }

            return false;
        }

        private byte[] buildVersion(String v) {
            try {
                String[] arr = v.split("\\.");
                return new byte[]{Byte.parseByte(arr[0]), Byte.parseByte(arr[1]), Byte.parseByte(arr[2])};
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return new byte[]{0, 0, 0};
        }

        @Override
        protected void onPostExecute(Boolean result) {
            //nav menu or other components always needs to be updated after we read the config.
            if (Ref.alive(contextReference)) {
                Context context = contextReference.get();
                Intent intent = new Intent(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE);
                intent.putExtra("value", result);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                if (ALWAYS_SHOW_UPDATE_DIALOG || (result && !isCancelled())) {
                    softwareUpdater.notifyUserAboutUpdate(context);
                }
            }

            // Even if we're offline, we need to disable these for the Google Play Distro.
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
                SearchEngine ytSE = SearchEngine.YOUTUBE;
                ytSE.setActive(false);

                SearchEngine scSE = SearchEngine.SOUNCLOUD;
                scSE.setActive(false);

                SearchEngine pixabaySE = SearchEngine.PIXABAY;
                pixabaySE.setActive(false);
            }
        }
    }
}
