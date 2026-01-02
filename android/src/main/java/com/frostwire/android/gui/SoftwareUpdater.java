/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
    private static String cachedSoundCloudClientId = null;
    private static String cachedSoundCloudAppVersion = null;

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
        if (update != null) {
            // Cache SoundCloud credentials if provided
            if (update.sc_client_id != null && !update.sc_client_id.trim().isEmpty()) {
                cachedSoundCloudClientId = update.sc_client_id;
                LOG.info("updateConfiguration: cached sc_client_id");
            }
            if (update.sc_app_version != null && !update.sc_app_version.trim().isEmpty()) {
                cachedSoundCloudAppVersion = update.sc_app_version;
                LOG.info("updateConfiguration: cached sc_app_version");
            }
        }

        if (update == null || update.config == null) {
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
        int supportThreshold = update.config.supportVpnThreshold;
        if (supportThreshold < 0 || supportThreshold > 100) {
            supportThreshold = 50;
        }
        CM.setInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, supportThreshold);

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

        /**
         * SoundCloud client ID for API requests
         */
        public String sc_client_id;

        /**
         * SoundCloud app version for API requests
         */
        public String sc_app_version;
    }

    @SuppressWarnings("CanBeFinal")
    private static class Config {
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        Map<String, Boolean> activeSearchEngines;
        int supportVpnThreshold = 50;
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

    /**
     * Gets the cached SoundCloud client ID from the remote update.
     * Returns a default value if not yet fetched or invalid.
     */
    public static String getSoundCloudClientId() {
        return cachedSoundCloudClientId != null ? cachedSoundCloudClientId : "dH1Xed1fpITYonugor6sw39jvdq58M3h";
    }

    /**
     * Gets the cached SoundCloud app version from the remote update.
     * Returns a default value if not yet fetched or invalid.
     */
    public static String getSoundCloudAppVersion() {
        return cachedSoundCloudAppVersion != null ? cachedSoundCloudAppVersion : "1766155513";
    }
}
