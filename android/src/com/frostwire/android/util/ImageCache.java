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
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;

import com.frostwire.android.util.DiskCache.Entry;
//import com.squareup.picasso.Cache;
//import com.squareup.picasso.LruCache;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

import static com.frostwire.android.gui.util.UIUtils.isMain;

/**
 * @author gubatron
 * @author aldenml
 */
final class ImageCache /*implements Cache*/ {

    private final DiskCache disk;
    //private final LruCache mem;

    ImageCache(Context context, File directory, long diskSize) {
        this.disk = createDiskCache(directory, diskSize);
        //this.mem = new LruCache(context);
    }

    //@Override
    public Bitmap get(String key) {
        Bitmap bmp = null;//mem.get(key);

        if (bmp == null && !isMain()) {
            bmp = diskGet(key);
        }

        return bmp;
    }

    //@Override
    public void set(String key, Bitmap bitmap) {
        //mem.set(key, bitmap);

        diskPut(key, bitmap);
    }

    //@Override
    public int size() {
        return /*mem.size()*/ + diskSize();
    }

    //@Override
    public int maxSize() {
        return /*mem.maxSize()*/ + diskMaxSize();
    }

    //@Override
    public void clear() {
        //mem.clear();
    }

    //@Override
    public void clearKeyUri(String keyPrefix) {
        //mem.clearKeyUri(keyPrefix);
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
}
