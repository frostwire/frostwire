/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.util;

import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.JdkHttpClient;
import com.frostwire.util.http.OkHttpClientWrapper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author gubatron
 * @author aldenml
 */
public class HttpClientFactory {
    private static Map<HttpContext, ThreadPool> okHttpClientPools = null;

    private static final boolean FORCE_JDK_HTTP_CLIENT = false;

    private static final Map<HttpContext, OkHttpClientWrapper> fwOKHTTPClients = new HashMap<>();
    private static final Object okHTTPClientLock = new Object();

    private HttpClientFactory() {
    }

    public static HttpClient newInstance() {
        return new JdkHttpClient();
    }

    public static HttpClient getInstance(HttpContext context) {
        if (FORCE_JDK_HTTP_CLIENT) {
            return new JdkHttpClient();
        }

        if (isWindowsXP()) {
            return new JdkHttpClient();
        }
        if (okHttpClientPools == null) {
            okHttpClientPools = buildThreadPools();
        }
        synchronized (okHTTPClientLock) {
            if (!fwOKHTTPClients.containsKey(context)) {
                fwOKHTTPClients.put(context, new OkHttpClientWrapper(okHttpClientPools.get(context)));
            }
        }
        return fwOKHTTPClients.get(context);
    }

    private static Map<HttpContext, ThreadPool> buildThreadPools() {
        final HashMap<HttpContext, ThreadPool> map = new HashMap<>();
        map.put(HttpContext.SEARCH, new ThreadPool("OkHttpClient-searches", 2, 2, 2, new LinkedBlockingQueue<>(), true));
        map.put(HttpContext.DOWNLOAD, new ThreadPool("OkHttpClient-downloads", 2, 2, 2, new LinkedBlockingQueue<>(), true));
        map.put(HttpContext.MISC, new ThreadPool("OkHttpClient-misc", 2, 2, 2, new LinkedBlockingQueue<>(), true));
        return map;
    }

    private static boolean isWindowsXP() {
        String os = System.getProperty("os.name");
        os = os.toLowerCase(Locale.US);
        return os.contains("windows xp");
    }

    public enum HttpContext {
        SEARCH,
        DOWNLOAD,
        MISC
    }
}
