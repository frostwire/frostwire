/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

import com.frostwire.logging.Logger;
import com.squareup.okhttp.internal.DiskLruCache;
import com.squareup.okhttp.internal.DiskLruCache.Editor;
import com.squareup.okhttp.internal.DiskLruCache.Snapshot;
import com.squareup.okhttp.internal.Util;
import com.squareup.okhttp.internal.io.FileSystem;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author gubatron
 * @author aldenml
 */
public final class DiskCache {

    private static final Logger LOG = Logger.getLogger(DiskCache.class);

    private static final int APP_VERSION = 1;
    private static final int VALUE_COUNT = 1;

    private final DiskLruCache cache;

    public DiskCache(File directory, long size) throws IOException {
        this.cache = DiskLruCache.create(FileSystem.SYSTEM, directory, APP_VERSION, VALUE_COUNT, size);
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

    public long maxSize() {
        return cache.getMaxSize();
    }

    public void delete() throws IOException {
        cache.delete();
    }

    private void writeTo(Editor editor, byte[] data) throws IOException {
        BufferedSink out = Okio.buffer(editor.newSink(0));
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private void writeTo(Editor editor, InputStream in) throws IOException {
        BufferedSink out = Okio.buffer(editor.newSink(0));
        Source source = Okio.source(in);
        try {
            out.writeAll(source);
        } finally {
            out.close();
        }
    }

    private String encodeKey(String key) {
        return Util.md5Hex(key);
    }

    public static final class Entry implements Closeable {

        private final Snapshot snapshot;

        public Entry(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        public InputStream getInputStream() {
            final Source source = snapshot.getSource(0);
            if (source == null) {
                return null;
            }
            return Okio.buffer(source).inputStream();
        }

        @Override
        public void close() {
            snapshot.close();
        }
    }
}
