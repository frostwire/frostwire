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

package com.frostwire.android;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;

import com.frostwire.android.gui.Librarian;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

import org.apache.commons.io.IOUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author gubatron
 * @author aldenml
 */
final class MediaScanner {

    private static final Logger LOG = Logger.getLogger(MediaScanner.class);

    public static void scanFiles(Context context, List<String> paths) {
        SystemUtils.safePost(Librarian.instance().getHandler(),
                () -> MediaScanner.scanFiles(context, paths, 6));
    }

    private static void scanFiles(final Context context, List<String> paths, int retries) {
        if (paths.size() == 0) {
            return;
        }

        LOG.info("scanFiles: About to scan files n: " + paths.size() + ", retries: " + retries);

        final LinkedList<String> failedPaths = new LinkedList<>();

        final CountDownLatch finishSignal = new CountDownLatch(paths.size());

        MediaScannerConnection.scanFile(context, paths.toArray(new String[0]), null, (path, uri) -> {
            try {
                boolean success = true;
                if (uri == null) {
                    success = false;
                    failedPaths.add(path);
                } else {
                    // verify the stored size four faulty scan
                    long size = getSize(context, uri);
                    if (size == 0) {
                        LOG.warn("scanFiles: Scan returned an uri but stored size is 0, path: " + path + ", uri:" + uri);
                        success = false;
                        failedPaths.add(path);
                    }
                }
                if (!success) {
                    LOG.info("scanFiles: Scan failed for path: " + path + ", uri: " + uri);
                } else {
                    LOG.info("scanFiles: Scan success for path: " + path + ", uri: " + uri);
                }
            } finally {
                finishSignal.countDown();
            }
        });

        try {
            finishSignal.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }

        if (failedPaths.size() > 0 && retries > 0) {
            // didn't want to do this, but there is a serious timing issue with the SD
            // and storage in general
            SystemClock.sleep(2000);
            scanFiles(context, failedPaths, retries - 1);
        }
    }

    private static long getSize(Context context, Uri uri) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{"_size"}, null, null, null);

            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Throwable e) {
            LOG.error("Error getting file size for uri: " + uri, e);
        } finally {
            IOUtils.closeQuietly(c);
        }

        return 0;
    }
}
