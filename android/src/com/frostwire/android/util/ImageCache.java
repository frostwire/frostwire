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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Looper;

import com.frostwire.android.util.DiskCache.Entry;
import com.squareup.picasso.Cache;
import com.squareup.picasso.LruCache;

/**
 * @author gubatron
 * @author aldenml
 * 
 */
final class ImageCache implements Cache {

    private final DiskCache disk;
    private final LruCache mem;

    public ImageCache(File directory, long diskSize, int memSize) {
        this.disk = createDiskCache(directory, diskSize);
        this.mem = new LruCache(memSize);
    }

    @Override
    public Bitmap get(String key) {
        Bitmap bmp = mem.get(key);

        if (bmp == null && !isMain()) {
            bmp = diskGet(key);
        }

        return bmp;
    }

    @Override
    public void set(String key, Bitmap bitmap) {
        mem.set(key, bitmap);

        diskPut(key, bitmap);
    }

    @Override
    public int size() {
        return mem.size() + diskSize();
    }

    @Override
    public int maxSize() {
        return mem.maxSize() + diskMaxSize();
    }

    @Override
    public void clear() {
        mem.clear();
    }

    @Override
    public void clearKeyUri(String keyPrefix) {
        mem.clearKeyUri(keyPrefix);
    }

    private InputStream getInputStream(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bmp.getByteCount());
        bmp.compress(CompressFormat.PNG, 100, out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private DiskCache createDiskCache(File directory, long diskSize) {
        try {
            return new DiskCache(directory, diskSize);
        } catch (Throwable e) {
            return null;
        }
    }

    private Bitmap diskGet(String key) {
        Bitmap bmp = null;

        if (disk != null) {
            try {
                Entry e = disk.get(key);
                if (e != null) {
                    try {
                        bmp = BitmapFactory.decodeStream(e.getInputStream());
                    } finally {
                        IOUtils.closeQuietly(e);
                    }

                    if (bmp == null) { // some error decoding
                        disk.remove(key);
                    }
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        return bmp;
    }

    private void diskPut(String key, Bitmap bitmap) {
        if (disk != null && !disk.containsKey(key)) {
            try {
                InputStream is = getInputStream(bitmap);
                try {
                    disk.put(key, is);
                } finally {
                    IOUtils.closeQuietly(is);
                }
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    private int diskSize() {
        return disk != null ? (int) disk.size() : 0;
    }

    private int diskMaxSize() {
        return disk != null ? (int) disk.maxSize() : 0;
    }

    private static boolean isMain() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }
}
