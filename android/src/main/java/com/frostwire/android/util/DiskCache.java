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

package com.frostwire.android.util;

import com.frostwire.util.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.internal.cache.DiskLruCache;
import okhttp3.internal.cache.DiskLruCache.Editor;
import okhttp3.internal.cache.DiskLruCache.Snapshot;
import okhttp3.internal.concurrent.TaskRunner;
import okio.BufferedSink;
import okio.ByteString;
import okio.FileSystem;
import okio.Okio;
import okio.Path;
import okio.Source;

/**
 * @author gubatron
 * @author aldenml
 */
public final class DiskCache {

    private static final Logger LOG = Logger.getLogger(DiskCache.class);

    private static final int APP_VERSION = 1;
    private static final int VALUE_COUNT_PER_CACHE_ENTRY = 2; // seen at okhttp3.Cache use of DiskLruCache constructor

    private final DiskLruCache cache;

    public DiskCache(File directory, long size) {
        okio.Path path = Path.get(directory);
        this.cache = new DiskLruCache(FileSystem.SYSTEM, path, APP_VERSION, VALUE_COUNT_PER_CACHE_ENTRY, size, TaskRunner.INSTANCE);
    }

    public boolean containsKey(String key) {
        try {
            return cache.get(encodeKey(key)) != null;
        } catch (IOException e) {
            LOG.warn("Error testing if the cache contains a key", e);
        }

        return false;
    }

    public Entry get(String key) {
        Entry entry = null;
        Snapshot snapshot;
        try {
            snapshot = cache.get(encodeKey(key));

            if (snapshot != null) {
                entry = new Entry(snapshot);
            }

        } catch (IOException e) {
            LOG.warn("Error getting value from internal DiskLruCache", e);
        }

        return entry;
    }

    public void put(String key, byte[] data) {
        Editor editor = null;

        try {
            editor = cache.edit(encodeKey(key));

            if (editor != null) {
                writeTo(editor, data);
                editor.commit();
            }

        } catch (IOException e) {
            LOG.warn("Error writing value to internal DiskLruCache", e);
            try {
                if (editor != null) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void put(String key, InputStream in) {
        Editor editor = null;

        try {
            editor = cache.edit(encodeKey(key));

            if (editor != null) {
                writeTo(editor, in);
                editor.commit();
            }

        } catch (IOException e) {
            LOG.warn("Error writing value to internal DiskLruCache", e);
            try {
                if (editor != null) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void remove(String key) {
        try {
            cache.remove(encodeKey(key));
        } catch (IOException e) {
            LOG.warn("Error deleting value from internal DiskLruCache: ", e);
        }
    }

    public long size() {
        try {
            return cache.size();
        } catch (IOException e) {
            LOG.warn("Error getting disk cache size", e);
        }

        return 0;
    }

    public long numEntries() {
        return -1; // don't have access to cache.lruEntries.size();
    }


    public long maxSize() {
        return cache.getMaxSize();
    }

    public void delete() throws IOException {
        cache.delete();
    }

    private void writeTo(Editor editor, byte[] data) throws IOException {
        try (BufferedSink out = Okio.buffer(editor.newSink(0))) {
            out.write(data);
        }
    }

    private void writeTo(Editor editor, InputStream in) throws IOException {
        try (BufferedSink out = Okio.buffer(editor.newSink(0))) {
            Source source = Okio.source(in);
            out.writeAll(source);
        }
    }

    private String encodeKey(String key) {
        return ByteString.encodeUtf8(key).md5().hex();
    }


    public static final class Entry implements Closeable {

        private final Snapshot snapshot;

        public Entry(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        public InputStream getInputStream() {
            final Source source = snapshot.getSource(0);
            return Okio.buffer(source).inputStream();
        }

        @Override
        public void close() {
            snapshot.close();
        }
    }
}
