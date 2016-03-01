/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.logging.Logger;
import com.frostwire.platform.Platforms;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.StringUtils;
import com.frostwire.uxstats.FlurryStats;
import com.frostwire.uxstats.UXStats;
import com.frostwire.uxstats.UXStatsConf;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoftwareUpdater {

    private static final Logger LOG = Logger.getLogger(SoftwareUpdater.class);

    public interface ConfigurationUpdateListener {
        void onConfigurationUpdate();
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

    public boolean isOldVersion() {
        return oldVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void checkForUpdate(final Context context) {
        long now = System.currentTimeMillis();

        if (now - updateTimestamp < UPDATE_MESSAGE_TIMEOUT) {
            return;
        }

        updateTimestamp = now;

        AsyncTask<Void, Void, Boolean> updateTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    final String basicOrPlus = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? "basic" : "plus";
                    final String userAgent = "FrostWire/android-" + basicOrPlus + "/" + Constants.FROSTWIRE_VERSION_STRING;
                    byte[] jsonBytes = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).
                            getBytes(Constants.SERVER_UPDATE_URL, 5000, userAgent, null);

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
                if (result && !isCancelled()) {
                    notifyUpdate(context);
                }

                // Even if we're offline, we need to disable these for the Google Play Distro.
                if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                    SearchEngine ytSE = SearchEngine.forName("YouTube");
                    ytSE.setActive(false);

                    SearchEngine scSE = SearchEngine.forName("Soundcloud");
                    scSE.setActive(false);
                }

                //nav menu always needs to be updated after we read the config.
                notifyConfigurationUpdateListeners();
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
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
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
        } catch (Throwable t) {

        }
    }

    private File getUpdateApk() {
        return Platforms.get().systemPaths().update();
    }

    private void notifyUpdate(final Context context) {
        try {
            if (update.a == null) {
                update.a = UPDATE_ACTION_OTA; // make it the old behavior
            }

            if (update.a.equals(UPDATE_ACTION_OTA)) {
                if (!getUpdateApk().exists()) {
                    return;
                }

                String message = StringUtils.getLocaleString(update.updateMessages, context.getString(R.string.update_message));

                UIUtils.showYesNoDialog(context,
                        R.drawable.app_icon,
                        message,
                        R.string.update_title,
                        update.changelog,
                        new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Engine.instance().stopServices(false);
                                UIUtils.openFile(context, getUpdateApk().getAbsolutePath(), Constants.MIME_TYPE_ANDROID_PACKAGE_ARCHIVE);
                            }
                        },
                        null, // negative listener
                        null  // bullet's listener
                );
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
        if (Constants.IS_BASIC_DEBUG) {
            myBuild += 900000;
        }
        LOG.info("isFrostWireOld(myBuild=" + myBuild + ", latestBuild=" + latestBuild + ")");
        boolean result;
        try {
            int latestBuildNum = Integer.parseInt(latestBuild);
            result = myBuild < latestBuildNum;
        } catch (Throwable ignored) {
            LOG.error("isFrostWireOld() can't parse latestBuild number.", ignored);
            result = false;
        }
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

        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE_THRESHOLD, new Random().nextInt(100) < update.config.supportThreshold);

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

        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_USE_MOBILE_CORE, update.config.mobileCore);
        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_USE_APPLOVIN, update.config.appLovin);
        ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_USE_INMOBI, update.config.inmobi);
        ConfigurationManager.instance().setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS, update.config.interstitialOffersTransferStarts);
        ConfigurationManager.instance().setInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES, update.config.interstitialTransferOffersTimeoutInMinutes);

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
            UXStats.instance().add3rdPartyAPI(new FlurryStats(activityContext));
        }
    }

    private void notifyConfigurationUpdateListeners() {
        for (ConfigurationUpdateListener listener : configurationUpdateListeners) {
            try {
                listener.onConfigurationUpdate();
            } catch (Throwable t) {
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
        public String vc;

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

        public List<String> changelog;

        public Map<String, String> updateMessages;
        public Map<String, String> marketMessages;
        public Config config;
    }

    @SuppressWarnings("CanBeFinal")
    private static class Config {
        public int supportThreshold = 100;
        public Map<String, Boolean> activeSearchEngines;
        public boolean mobileCore = false;
        public boolean appLovin = false;
        public boolean inmobi = false;
        public int interstitialOffersTransferStarts = 5;
        public int interstitialTransferOffersTimeoutInMinutes = 15;

        // ux stats
        public boolean uxEnabled = false;
        public int uxPeriod = 3600;
        public int uxMinEntries = 10;
        public int uxMaxEntries = 10000;
    }

    public void removeConfigurationUpdateListener(Object slideMenuFragment) {
        if (configurationUpdateListeners.size() > 0) {
            configurationUpdateListeners.remove(slideMenuFragment);
        }
    }
}
