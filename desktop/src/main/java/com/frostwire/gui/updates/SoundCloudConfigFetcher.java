/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.gui.updates;

import com.frostwire.util.Logger;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.http.HttpClient;

import java.net.URI;

/**
 * Fetches and caches SoundCloud configuration (client_id and app_version) from the remote update server.
 * This allows for dynamic updates of SoundCloud API credentials without requiring app updates.
 * This is the single source of truth for SoundCloud credentials fallback values.
 */
public final class SoundCloudConfigFetcher {
    private static final Logger LOG = Logger.getLogger(SoundCloudConfigFetcher.class);
    private static final String ANDROID_UPDATE_URL = "https://update.frostwire.com/android";
    private static final int TIMEOUT = 10000; // 10 seconds

    // Fallback constants - these are the single source of truth for default credentials
    public static final String DEFAULT_CLIENT_ID = "iuspDvaXDbD3AnFwLWK56Fk69q56xsKu";
    public static final String DEFAULT_APP_VERSION = "1766155513";

    // In-memory cache
    private static String cachedClientId = DEFAULT_CLIENT_ID;
    private static String cachedAppVersion = DEFAULT_APP_VERSION;
    private static long lastFetchTime = 0;
    private static final long CACHE_DURATION = 24 * 60 * 60 * 1000; // 24 hours

    private static class RemoteConfig {
        public String sc_client_id;
        public String sc_app_version;
    }

    /**
     * Fetches the SoundCloud configuration from the remote server.
     * Uses in-memory caching to avoid excessive network requests.
     * Validates fetched credentials against the API before caching them;
     * falls back to defaults if the server credentials are stale/expired.
     *
     * @return true if fetch was successful or cache is still valid, false otherwise
     */
    public static boolean fetchAndUpdateConfig() {
        long now = System.currentTimeMillis();

        // Return true if cache is still valid
        if (now - lastFetchTime < CACHE_DURATION && lastFetchTime > 0) {
            LOG.info("SoundCloudConfigFetcher: Using cached config (age: " + (now - lastFetchTime) / 1000 + "s)");
            return true;
        }

        try {
            LOG.info("SoundCloudConfigFetcher: Fetching SoundCloud config from " + ANDROID_UPDATE_URL);
            HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            byte[] responseBytes = httpClient.getBytes(ANDROID_UPDATE_URL, TIMEOUT, null, null);

            if (responseBytes != null) {
                RemoteConfig config = JsonUtils.toObject(new String(responseBytes), RemoteConfig.class);
                if (config != null && config.sc_client_id != null && config.sc_app_version != null) {
                    // Validate the fetched credentials; fall back to defaults if they're stale/expired
                    if (validateCredentials(config.sc_client_id)) {
                        cachedClientId = config.sc_client_id;
                        cachedAppVersion = config.sc_app_version;
                        LOG.info("SoundCloudConfigFetcher: Successfully updated SoundCloud config");
                    } else {
                        LOG.warn("SoundCloudConfigFetcher: Remote credentials invalid (API rejected them), keeping defaults");
                        cachedClientId = DEFAULT_CLIENT_ID;
                        cachedAppVersion = DEFAULT_APP_VERSION;
                    }
                    lastFetchTime = now;
                    LOG.info("SoundCloudConfigFetcher: client_id=" + cachedClientId + ", app_version=" + cachedAppVersion);
                    return true;
                } else {
                    LOG.warn("SoundCloudConfigFetcher: Remote config missing required fields");
                    return false;
                }
            } else {
                LOG.warn("SoundCloudConfigFetcher: Could not fetch config from " + ANDROID_UPDATE_URL);
                return false;
            }
        } catch (Throwable e) {
            LOG.error("SoundCloudConfigFetcher: Failed to fetch SoundCloud config: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validates SoundCloud credentials by making a minimal API probe.
     * Returns true if the credentials are accepted by the API (HTTP 200), false otherwise.
     */
    private static boolean validateCredentials(String clientId) {
        try {
            HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            String url = "https://api-v2.soundcloud.com/search/tracks?q=test&limit=1&client_id=" + clientId;
            byte[] bytes = httpClient.getBytes(url, 5000, null, null);
            boolean valid = bytes != null && bytes.length > 10;
            if (!valid) {
                LOG.warn("SoundCloudConfigFetcher: Credential validation failed for client_id=" + clientId);
            }
            return valid;
        } catch (Throwable e) {
            LOG.warn("SoundCloudConfigFetcher: Could not validate credentials: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the cached SoundCloud client ID.
     *
     * @return the cached client ID (never null, returns default if not yet fetched)
     */
    public static String getClientId() {
        return cachedClientId;
    }

    /**
     * Gets the cached SoundCloud app version.
     *
     * @return the cached app version (never null, returns default if not yet fetched)
     */
    public static String getAppVersion() {
        return cachedAppVersion;
    }
}
