/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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
import android.os.Process;
import android.os.StatFs;

import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemUtils {

    private static final Logger LOG = Logger.getLogger(SystemUtils.class);

    private static final int VERSION_SDK_NOUGAT_7_0 = 24;

    private SystemUtils() {
    }

    public static File getCacheDir(Context context, String directory) {
        return new File(context.getExternalFilesDir(null),
                "cache" + File.separator + directory);
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

        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(path));
    }

    public static boolean isPrimaryExternalPath(File path) {
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        return path != null ? path.getAbsolutePath().startsWith(primary) : null;
    }

    public static long getAvailableStorageSize(File dir) {
        long size;
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            size = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Throwable e) {
            size = -1; // system error computing the available storage size
        }

        return size;
    }

    /**
     * Use this instead ContextCompat
     */
    public static File[] getExternalFilesDirs(Context context) {
        List<File> dirs = new LinkedList<>();

        for (File f : context.getExternalFilesDirs(null)) {
            if (f != null) {
                dirs.add(f);
            }
        }

        return dirs.toArray(new File[0]);
    }

    private static boolean hasSdkOrNewer(int versionCode) {
        return Build.VERSION.SDK_INT >= versionCode;
    }

    /**
     * Used to determine if the device is running
     * Nougat (Android 7.0) or greater.
     *
     * @return {@code true} if the device is running KitKat or greater,
     * {@code false} otherwise
     */
    public static boolean hasNougatOrNewer() {
        return hasSdkOrNewer(VERSION_SDK_NOUGAT_7_0);
    }

    /** Used to determine if the device is running Android11 or greater */
    public static boolean hasAndroid10OrNewer() {
        return hasSdkOrNewer(Build.VERSION_CODES.Q);
    }

    /** Used to determine if the device is running Android11 or greater */
    public static boolean hasAndroid11OrNewer() {
        return hasSdkOrNewer(30); //Build.VERSION_CODES.R
    }

    /**
     * @param context
     * @param timeout        timeout in ms. set to -1 to wait forever.
     * @param serviceClasses
     */
    public static void waitWhileServicesAreRunning(Context context, long timeout, Class<?>... serviceClasses) {
        final long startTime = System.currentTimeMillis();
        Set<Class<?>> servicesRunning = new HashSet<>();
        Collections.addAll(servicesRunning, serviceClasses);
        while (!servicesRunning.isEmpty()) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            long timeLeft = timeout - elapsedTime;
            Iterator<Class<?>> iterator = servicesRunning.iterator();
            while (iterator != null && iterator.hasNext()) {
                Class serviceClass = iterator.next();
                if (isServiceRunning(context, serviceClass)) {
                    LOG.info("waitWhileServicesAreRunning(...): " + serviceClass.getSimpleName() + " is still running. (" + timeLeft + " ms left)");
                    break;
                } else {
                    LOG.info("waitWhileServicesAreRunning(...): " + serviceClass.getSimpleName() + " is shutdown. (" + timeLeft + " ms left)");
                    iterator.remove();
                }
            }

            try {
                if (timeout != -1 && elapsedTime > timeout) {
                    LOG.info("waitWhileServicesAreRunning(...) timed out, exiting now (" + timeout + "ms)");
                    break;
                }
                if (!servicesRunning.isEmpty()) {
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

    /**
     * This method schedule a hard kill process in a background thread.
     * <p>
     * It first waits for major components like services to shutdown to alleviate
     * the problem that the android OS restarts the application.
     */
    public static void requestKillProcess(final Context context) {
        // we make sure all services have finished shutting down before we kill our own process.
        Thread t = new Thread("shutdown-halt") {
            @Override
            public void run() {
                SystemUtils.waitWhileServicesAreRunning(context, 15000, MusicPlaybackService.class, EngineService.class);
                LOG.info("MainActivity::shutdown()/shutdown-halt thread: android.os.Process.killProcess(" + Process.myPid() + ")");
                Process.killProcess(android.os.Process.myPid());
            }
        };

        t.setDaemon(false);
        t.start();
    }
}
