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
