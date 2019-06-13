/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.JdkHttpClient;
import com.frostwire.util.http.OKHTTPClient;

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

    private HttpClientFactory() {
    }

    public static HttpClient newInstance() {
        return new JdkHttpClient();
    }

    public static HttpClient getInstance(HttpContext context) {
        if (isWindowsXP()) {
            return new JdkHttpClient();
        }
        if (okHttpClientPools == null) {
            okHttpClientPools = buildThreadPools();
        }
        return new OKHTTPClient(okHttpClientPools.get(context));
    }

    private static Map<HttpContext, ThreadPool> buildThreadPools() {
        final HashMap<HttpContext, ThreadPool> map = new HashMap<>();
        map.put(HttpContext.SEARCH, new ThreadPool("OkHttpClient-searches", 1, 5, 60, new LinkedBlockingQueue<Runnable>(), true));
        map.put(HttpContext.DOWNLOAD, new ThreadPool("OkHttpClient-downloads", 1, 10, 5, new LinkedBlockingQueue<Runnable>(), true));
        map.put(HttpContext.MISC, new ThreadPool("OkHttpClient-misc", 2, 10, 30, new LinkedBlockingQueue<Runnable>(), true));
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
