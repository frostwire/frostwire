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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.offers.Offers;
import com.frostwire.platform.Platforms;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.UXStats;
import com.frostwire.uxstats.UXStatsConf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoftwareUpdater {

    private static final Logger LOG = Logger.getLogger(SoftwareUpdater.class);
    private final boolean ALWAYS_SHOW_UPDATE_DIALOG = false; // debug flag.

    public interface ConfigurationUpdateListener {
        void onConfigurationUpdate(boolean updateAvailable);
    }

    private static final String TAG = "FW.SoftwareUpdater";

    private static final long UPDATE_MESSAGE_TIMEOUT = 30 * 60 * 1000; // 30 minutes

    private static final String UPDATE_ACTION_OTA = "ota";
    private static final String UPDATE_ACTION_MARKET = "market";

    private boolean oldVersion;
    private String latestVersion;
    private Update update;
    private long updateTimestamp;

    private final Set<ConfigurationUpdateListener> configurationUpdateListeners;

    private static SoftwareUpdater instance;

    public static SoftwareUpdater instance() {
        if (instance == null) {
            instance = new SoftwareUpdater();
        }
        return instance;
    }

    private SoftwareUpdater() {
        this.oldVersion = false;
        this.latestVersion = Constants.FROSTWIRE_VERSION_STRING;
        this.configurationUpdateListeners = new HashSet<>();
    }

    public void checkForUpdate(final Context context) {
        long now = System.currentTimeMillis();

        if (context instanceof ConfigurationUpdateListener) {
            addConfigurationUpdateListener((ConfigurationUpdateListener) context);
        }

        if (now - updateTimestamp < UPDATE_MESSAGE_TIMEOUT) {
            return;
        }

        updateTimestamp = now;

        AsyncTask<Void, Void, Boolean> updateTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    byte[] jsonBytes = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).
                            getBytes(Constants.SERVER_UPDATE_URL, 5000, Constants.USER_AGENT, null);

                    if (jsonBytes != null) {
                        update = JsonUtils.toObject(new String(jsonBytes), Update.class);

                        if (update.vc != null) {
                            oldVersion = isFrostWireOld(BuildConfig.VERSION_CODE, update.vc);
                        } else {
                            latestVersion = update.v;
                            String[] latestVersionArr = latestVersion.split("\\.");

                            // lv = latest version
                            byte[] lv = new byte[]{Byte.valueOf(latestVersionArr[0]), Byte.valueOf(latestVersionArr[1]), Byte.valueOf(latestVersionArr[2])};

                            // mv = my version
                            byte[] mv = buildVersion(Constants.FROSTWIRE_VERSION_STRING);

                            oldVersion = isFrostWireOld(mv, lv);
                        }

                        updateConfiguration(update, context);
                    } else {
                        LOG.warn("Could not fetch update information from " + Constants.SERVER_UPDATE_URL);
                    }

                    return handleOTAUpdate();
                } catch (Throwable e) {
                    Log.e(TAG, "Failed to check/retrieve/update the update information", e);
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

                if (ALWAYS_SHOW_UPDATE_DIALOG || (result && !isCancelled())) {
                    notifyUserAboutUpdate(context);
                }

                // Even if we're offline, we need to disable these for the Google Play Distro.
                if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
                    SearchEngine ytSE = SearchEngine.forName("YouTube");
                    ytSE.setActive(false);

                    SearchEngine scSE = SearchEngine.forName("Soundcloud");
                    scSE.setActive(false);
                }

                //nav menu or other components always needs to be updated after we read the config.
                notifyConfigurationUpdateListeners(result);
            }
        };

        // TODO: Use our executors and a runnable instead.
        updateTask.execute();
    }

    /**
     * @return true if there's an update available.
     * @throws IOException
     */
    private boolean handleOTAUpdate() throws IOException {
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG) {
            LOG.info("handleOTAUpdate(): it's Google Play, aborting -> false");
            return false;
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

    public void addConfigurationUpdateListener(ConfigurationUpdateListener listener) {
        try {
            configurationUpdateListeners.add(listener);
        } catch (Throwable ignored) {
            LOG.error("Could not add configuration update listener", ignored);
        }
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

                LOG.info("notifyUserAboutUpdate(): showing update dialog.");
                SoftwareUpdaterDialog.newInstance(update).show(((Activity) context).getFragmentManager());
            } else if (update.a.equals(UPDATE_ACTION_MARKET)) {

                String message = StringUtils.getLocaleString(update.marketMessages, context.getString(R.string.update_message));

                UIUtils.showYesNoDialog(context, R.drawable.app_icon, message, R.string.update_title, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(update.m));
                        context.startActivity(intent);
                    }
                });
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to notify update", e);
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
            myBuild += 40000;
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
        CM.setInt(Constants.PREF_KEY_GUI_MOPUB_PREVIEW_BANNER_THRESHOLD, update.config.mopubPreviewBannerThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_MOPUB_ALBUM_ART_BANNER_THRESHOLD, update.config.mopubAlbumArtBannerThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD, update.config.removeAdsB2bThreshold);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS, update.config.interstitialOffersTransferStarts);
        CM.setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES, update.config.interstitialTransferOffersTimeoutInMinutes);

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

    private void notifyConfigurationUpdateListeners(boolean result) {
        for (ConfigurationUpdateListener listener : configurationUpdateListeners) {
            try {
                listener.onConfigurationUpdate(result);
            } catch (Throwable ignored) {
                LOG.error(ignored.getMessage(), ignored);
            }
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
        int interstitialOffersTransferStarts = 5;
        int interstitialTransferOffersTimeoutInMinutes = 15;

        // ux stats
        boolean uxEnabled = false;
        int uxPeriod = 3600;
        int uxMinEntries = 10;
        int uxMaxEntries = 10000;
    }

    public void removeConfigurationUpdateListener(Object slideMenuFragment) {
        if (configurationUpdateListeners.size() > 0) {
            configurationUpdateListeners.remove(slideMenuFragment);
        }
    }

    private static class PositiveButtonOnClickListener implements View.OnClickListener {
        private final Context context;
        private final Dialog newSoftwareUpdaterDialog;

        PositiveButtonOnClickListener(Context context, Dialog newSoftwareUpdaterDialog) {
            this.context = context;
            this.newSoftwareUpdaterDialog = newSoftwareUpdaterDialog;
        }

        @Override
        public void onClick(View v) {
            Engine.instance().stopServices(false);
            UIUtils.openFile(context, getUpdateApk().getAbsolutePath(), Constants.MIME_TYPE_ANDROID_PACKAGE_ARCHIVE, false);
            newSoftwareUpdaterDialog.dismiss();
        }
    }

    private static class NegativeButtonOnClickListener implements View.OnClickListener {
        private final Dialog newSoftwareUpdaterDialog;

        NegativeButtonOnClickListener(Dialog newSoftwareUpdaterDialog) {
            this.newSoftwareUpdaterDialog = newSoftwareUpdaterDialog;
        }

        @Override
        public void onClick(View v) {
            newSoftwareUpdaterDialog.dismiss();
        }
    }

    public static class SoftwareUpdaterDialog extends AbstractDialog {
        private static Update update;

        public static SoftwareUpdaterDialog newInstance(Update update) {
            SoftwareUpdaterDialog.update = update;
            return new SoftwareUpdaterDialog();
        }

        public SoftwareUpdaterDialog() {
            super(R.layout.dialog_default_update);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            String message = StringUtils.getLocaleString(update.updateMessages, getString(R.string.update_message));

            TextView title = findView(dlg, R.id.dialog_default_update_title);
            title.setText(R.string.update_title);

            TextView text = findView(dlg, R.id.dialog_default_update_text);
            text.setText(message);

            final ListView listview = findView(dlg, R.id.dialog_default_update_list_view);
            String[] values = new String[update.changelog.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = String.valueOf(Html.fromHtml("&#8226; " + update.changelog.get(i)));
            }

            final ArrayAdapter adapter = new ArrayAdapter(getActivity(),
                    R.layout.dialog_update_bullet,
                    R.id.dialog_update_bullets_checked_text_view,
                    values);
            listview.setAdapter(adapter);

            // Set the save button action
            Button noButton = findView(dlg, R.id.dialog_default_update_button_no);
            noButton.setText(R.string.cancel);

            Button yesButton = findView(dlg, R.id.dialog_default_update_button_yes);
            yesButton.setText(android.R.string.ok);
            yesButton.setOnClickListener(new PositiveButtonOnClickListener(getActivity(), dlg));
            noButton.setOnClickListener(new NegativeButtonOnClickListener(dlg));
        }
    }
}
