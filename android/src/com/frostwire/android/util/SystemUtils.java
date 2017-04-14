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

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemUtils {

    private static final Logger LOG = Logger.getLogger(SystemUtils.class);

    private static final int VERSION_CODE_KITKAT = 19;

    private SystemUtils() {
    }

    public static File getCacheDir(Context context, String directory) {
        File cache;

        if (isPrimaryExternalStorageMounted()) {
            cache = context.getExternalCacheDir();
        } else {
            cache = context.getCacheDir();
        }

        return new File(cache, directory);
    }

    public static int calculateMemoryCacheSize(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        boolean largeHeap = (context.getApplicationInfo().flags & FLAG_LARGE_HEAP) != 0;
        int memoryClass = am.getMemoryClass();
        if (largeHeap) {
            memoryClass = am.getLargeMemoryClass();
        }
        // Target ~15% of the available heap.
        return 1024 * 1024 * memoryClass / 7;
    }

    public static long calculateDiskCacheSize(File dir, int minSize, int maxSize) {
        long size = minSize;

        try {
            StatFs statFs = new StatFs(dir.getAbsolutePath());
            // TODO: upgrade these deprecated calls whenever we move past API 18
            // to getBlockCountLong() and getBlockSizeLong().
            long available = ((long) statFs.getBlockCount()) * statFs.getBlockSize();
            // Target 2% of the total space.
            size = available / 50;
        } catch (IllegalArgumentException ignored) {
        }

        // Bound inside min/max size for disk cache.
        return Math.max(Math.min(size, maxSize), minSize);
    }

    /**
     * returns true if the media is present
     * and mounted at its mount point with read/write access.
     *
     * @see android.os.Environment#MEDIA_MOUNTED
     */
    public static boolean isPrimaryExternalStorageMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Use this instead of EnvironmentCompat.
     * <p/>
     * returns true if the media is present
     * and mounted at its mount point with read/write access.
     *
     * @see android.os.Environment#MEDIA_MOUNTED
     */
    public static boolean isSecondaryExternalStorageMounted(File path) {
        if (path == null) { // fast precondition
            return false;
        }

        boolean result = false;

        if (hasKitKatOrNewer()) {
            result = Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(path));
        } else {
            try {
                String[] l = path.list();
                result = l != null && l.length > 0;
            } catch (Throwable e) {
                LOG.error("Error detecting secondary external storage state", e);
            }
        }

        return result;
    }

    public static boolean isPrimaryExternalPath(File path) {
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        return path != null ? path.getAbsolutePath().startsWith(primary) : null;
    }

    public static long getAvailableStorageSize(File dir) {
        long size;
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            size = ((long) stat.getAvailableBlocks()) * stat.getBlockSize();
        } catch (Throwable e) {
            size = -1; // system error computing the available storage size
        }

        return size;
    }

    /**
     * Use this instead ContextCompat
     *
     */
    public static File[] getExternalFilesDirs(Context context) {
        if (hasKitKatOrNewer()) {
            List<File> dirs = new LinkedList<>();

            for (File f : ContextCompat.getExternalFilesDirs(context, null)) {
                if (f != null) {
                    dirs.add(f);
                }
            }

            return dirs.toArray(new File[dirs.size()]);
        } else {
            List<File> dirs = new LinkedList<>();

            dirs.add(context.getExternalFilesDir(null));

            try {
                String secondaryStorages = System.getenv("SECONDARY_STORAGE");
                if (secondaryStorages != null) {
                    String[] storages = secondaryStorages.split(File.pathSeparator);
                    for (String s : storages) {
                        dirs.add(new File(s));
                    }
                }
            } catch (Throwable e) {
                LOG.error("Unable to get secondary external storages", e);
            }

            return dirs.toArray(new File[dirs.size()]);
        }
    }

    private static boolean hasSdkOrNewer(int versionCode) {
        return Build.VERSION.SDK_INT >= versionCode;
    }

    /**
     * Used to determine if the device is running
     * KitKat (Android 4.4) or greater
     *
     * @return True if the device is running KitKat or greater,
     * false otherwise
     */
    public static boolean hasKitKatOrNewer() {
        return hasSdkOrNewer(VERSION_CODE_KITKAT);
    }

    /**
     *
     * @param context
     * @param timeout timeout in ms. set to -1 to wait forever.
     * @param serviceClasses
     */
    public static void waitWhileServicesAreRunning(Context context, long timeout, Class<?> ... serviceClasses) {
        final long startTime = System.currentTimeMillis();
        Set<Class<?>> serviceClassesNotRunningAnymore = new HashSet<>();
        while (serviceClasses.length != serviceClassesNotRunningAnymore.size()) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            long timeLeft = timeout - elapsedTime;
            for (Class serviceClass : serviceClasses) {
                if (isServiceRunning(context, serviceClass)) {
                    LOG.info("waitWhileServicesAreRunning(...): " + serviceClass.getSimpleName() + " is still running. (" + timeLeft + " ms left)");
                    break;
                } else {
                    LOG.info("waitWhileServicesAreRunning(...): " + serviceClass.getSimpleName() + " is shutdown. (" + timeLeft + " ms left)");
                    serviceClassesNotRunningAnymore.add(serviceClass);
                }
            }

            try {
                if (timeout != -1 && elapsedTime > timeout) {
                    LOG.info("waitWhileServicesAreRunning(...) timed out, exiting now (" + timeout + "ms)");
                    break;
                }

                if (serviceClasses.length != serviceClassesNotRunningAnymore.size()) {
                    LOG.info("waitWhileServicesAreRunning(...) zzz... zzz... (150ms)");
                    Thread.sleep(150);
                } else {
                    LOG.info("waitWhileServicesAreRunning(...) no more wait, all services shutdown!");
                    break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
