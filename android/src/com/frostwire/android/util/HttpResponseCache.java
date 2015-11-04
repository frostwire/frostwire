/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.android.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import android.content.Context;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
public final class HttpResponseCache extends ResponseCache implements Closeable {

    private static final int MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    private final android.net.http.HttpResponseCache delegate;

    private HttpResponseCache(android.net.http.HttpResponseCache delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() throws IOException {
        if (ResponseCache.getDefault() == this.delegate) {
            ResponseCache.setDefault(null);
        }
        delegate.close();
    }

    @Override
    public CacheResponse get(URI uri, String requestMethod, Map<String, List<String>> requestHeaders) throws IOException {
        return delegate.get(uri, requestMethod, requestHeaders);
    }

    @Override
    public CacheRequest put(URI uri, URLConnection connection) throws IOException {
        return delegate.put(uri, connection);
    }

    public static HttpResponseCache install(Context context) throws IOException {
        ResponseCache installed = ResponseCache.getDefault();
        if (installed instanceof Closeable) {
            ((Closeable) installed).close();
        }

        File directory = SystemUtils.getCacheDir(context, "http");
        long maxSize = SystemUtils.calculateDiskCacheSize(directory, MIN_DISK_CACHE_SIZE, MAX_DISK_CACHE_SIZE);

        return new HttpResponseCache(android.net.http.HttpResponseCache.install(directory, maxSize));
    }
}