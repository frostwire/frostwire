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

import android.content.Context;

import com.frostwire.android.util.DiskCache;
import com.frostwire.android.util.DiskCache.Entry;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.CrawlCache;
import com.frostwire.util.Logger;

import org.apache.commons.io.IOUtils;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class DiskCrawlCache implements CrawlCache {

    private static final Logger LOG = Logger.getLogger(DiskCrawlCache.class);

    private static final int MAX_DISK_CACHE_SIZE = 50 * 1024 * 1024; // 50MB

    private final File directory;
    private DiskCache cache;

    DiskCrawlCache(Context context) {
        this.directory = SystemUtils.getCacheDir(context, "search");
        this.cache = createDiskCache(directory, MAX_DISK_CACHE_SIZE);
    }

    @Override
    public byte[] get(String key) {
        byte[] data = null;

        if (cache != null) {
            try {
                Entry e = cache.get(key);
                if (e != null) {
                    try {
                        data = IOUtils.toByteArray(e.getInputStream());
                    } finally {
                        e.close();
                    }
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        return data;
    }

    @Override
    public void put(String key, byte[] data) {
        if (cache != null) {
            try {
                cache.put(key, data);
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    @Override
    public void remove(String key) {
        if (cache != null) {
            try {
                cache.remove(key);
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    @Override
    public void clear() {
        if (cache != null) {
            try {
                cache.delete();
                cache = createDiskCache(directory, MAX_DISK_CACHE_SIZE);
            } catch (Throwable e) {
                LOG.error("Unable to clear the crawl cache", e);
            }
        }
    }

    @Override
    public long sizeInBytes() {
        long size = 0;
        if (cache != null) {
            try {
                size = cache.size();
            } catch (Throwable e) {
                LOG.error("Unable to get crawl cache size", e);
            }
        }

        return size;
    }

    @Override
    public long numEntries() {
        long size = 0;
        if (cache != null) {
            try {
                size = cache.numEntries();
            } catch (Throwable e) {
                LOG.error("Unable to get crawl cache number of entries", e);
            }
        }

        return size;
    }

    private DiskCache createDiskCache(File directory, long diskSize) {
        try {
            return new DiskCache(directory, diskSize);
        } catch (Throwable e) {
            return null;
        }
    }
}
