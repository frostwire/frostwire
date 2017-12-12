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

package com.frostwire.android.util;


import android.content.Context;
import android.os.StatFs;
import android.support.annotation.NonNull;

import com.frostwire.util.http.OKHTTPClient;
import com.squareup.picasso.Downloader;

import java.io.File;
import java.io.IOException;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Provides an OkHttp based implementation, but with a custom SSL
 * configuration to ignore/accept any certificate checks or verifications.
 *
 * @author gubatron
 * @author aldenml
 */
final class ImageLoaderDownloader implements Downloader {

    private final Cache cache;
    private final Call.Factory client;

    ImageLoaderDownloader(Context context) {
        File cacheDir = createDefaultCacheDir(context);
        long maxSize = calculateDiskCacheSize(cacheDir);

        this.cache = new Cache(cacheDir, maxSize);

        OkHttpClient.Builder b = new OkHttpClient.Builder();
        b = b.cache(cache);
        b = OKHTTPClient.configNullSsl(b);
        this.client = b.build();
    }


    @NonNull
    @Override
    public Response load(@NonNull Request request) throws IOException {
        return client.newCall(request).execute();
    }

    @Override
    public void shutdown() {
        try {
            cache.close();
        } catch (IOException ignored) {
        }
    }

    // ------- below code copied from com.squareup.picasso.Utils -------
    // copied here to keep code independence

    private static final String PICASSO_CACHE = "picasso-downloader";
    private static final int MIN_DISK_CACHE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    private static File createDefaultCacheDir(Context context) {
        File cache = SystemUtils.getCacheDir(context, PICASSO_CACHE);
        if (!cache.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cache.mkdirs();
        }
        return cache;
    }

    private static long calculateDiskCacheSize(File dir) {
        long size = MIN_DISK_CACHE_SIZE;

        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            long blockCount = statFs.getBlockCountLong();
            long blockSize = statFs.getBlockSizeLong();
            long available = blockCount * blockSize;
            // Target 2% of the total space.
            size = available / 50;
        } catch (IllegalArgumentException ignored) {
        }

        // Bound inside min/max size for disk cache.
        return Math.max(Math.min(size, MAX_DISK_CACHE_SIZE), MIN_DISK_CACHE_SIZE);
    }
}
