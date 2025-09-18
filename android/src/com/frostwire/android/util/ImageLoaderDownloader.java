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
