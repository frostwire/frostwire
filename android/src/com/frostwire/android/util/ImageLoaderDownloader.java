/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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


import com.squareup.picasso.Downloader;

import java.io.IOException;

import androidx.annotation.NonNull;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * NOTE: this class will be removed in the near future when Picasso
 * is updated to the latest SNAPSHOT
 *
 * Provides an OkHttp based implementation, but with a custom SSL
 * configuration to ignore/accept any certificate checks or verifications.
 *
 * @author gubatron
 * @author aldenml
 */
final class ImageLoaderDownloader implements Downloader {

    private final Cache cache;
    private final Call.Factory client;

    ImageLoaderDownloader(OkHttpClient client) {
        this.cache = client.cache();
        this.client = client;
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
}
