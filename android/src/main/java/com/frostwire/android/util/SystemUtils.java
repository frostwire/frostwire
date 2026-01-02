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

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemUtils {

    private static final Logger LOG = Logger.getLogger(SystemUtils.class);

    // TIP: https://apilevels.com/
    private static final int VERSION_SDK_NOUGAT_7_0_N = 24;
    private static final int VERSION_SDK_ANDROID_10_Q = 29;
    private static final int VERSION_SDK_ANDROID_11_R = 30;
    private static final int VERSION_SDK_ANDROID_12_S = 31;
    private static final int VERSION_SDK_ANDROID_13_TIRAMISU = 33;

    private SystemUtils() {
    }

    public static Context getApplicationContext() {
        Context context;
        try {
            context = Engine.instance().getApplication().getApplicationContext();
            if (context == null) {
                context = MainApplication.context();
            }
        } catch (Throwable ignored) {
            context = MainApplication.context();
        }
        return context;
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isPrimaryExternalPath(File path) {
        String primary = Environment.getExternalStorageDirectory().getAbsolutePath();
        return path != null && path.getAbsolutePath().startsWith(primary);
    }

    private static boolean hasSdkOrNewer(int versionCode) {
        return Build.VERSION.SDK_INT >= versionCode;
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean hasSdk(int versionCode) {
        return Build.VERSION.SDK_INT == versionCode;
    }

    /**
     * Used to determine if the device is running
     * Nougat (Android 7.0) or greater.
     *
     * @return {@code true} if the device is running KitKat or greater,
     * {@code false} otherwise
     */
    public static boolean hasNougatOrNewer() {
        return hasSdkOrNewer(VERSION_SDK_NOUGAT_7_0_N);
    }

    /**
     * Used to determine if the device is running Android10 or greater
     */
    public static boolean hasAndroid10OrNewer() {
        return hasSdkOrNewer(VERSION_SDK_ANDROID_10_Q);
    }

    public static boolean hasAndroid10() {
        return hasSdk(VERSION_SDK_ANDROID_10_Q);
    }

    /**
     * Used to determine if the device is running Android11 or greater
     */
    public static boolean hasAndroid11OrNewer() {
        return hasSdkOrNewer(VERSION_SDK_ANDROID_11_R);
    }

    /**
     * Used to determine if the device is running Android12 or greater
     *
     * @noinspection unused
     */
    public static boolean hasAndroid12OrNewer() {
        return hasSdkOrNewer(VERSION_SDK_ANDROID_12_S);
    }

    public static boolean hasAndroid13OrNewer() {
        return hasSdkOrNewer(VERSION_SDK_ANDROID_13_TIRAMISU);
    }

    /**
     * We call it "safe" because if any exceptions are thrown,
     * they are caught in order to not crash the handler thread.
     */
    public static void exceptionSafePost(Handler handler, Runnable r) {
        if (handler != null) {
            // We are already in the Handler thread, just go!
            if (Thread.currentThread() == handler.getLooper().getThread()) {
                try {
                    r.run();
                } catch (Throwable t) {
                    LOG.error("safePost() " + t.getMessage(), t);
                }
            } else {
                handler.post(() -> exceptionSafePost(handler, r));
            }
        }
    }

    public static void ensureBackgroundThreadOrCrash(String classAndMethodNames) {
        if (isUIThread()) {
            throw new RuntimeException("ensureBackgroundThreadOrCrash: " + classAndMethodNames + " should not be on main thread.");
        }
    }

    public static void ensureUIThreadOrCrash(String classAndMethodNames) {
        if (!isUIThread()) {
            Thread t = Thread.currentThread();
            throw new RuntimeException("ensureUIThreadOrCrash: " + classAndMethodNames + " should be on main thread. Invoked from tid=" + t.getId() + ":" + t.getName());
        }
    }

    public static void postToUIThread(Runnable runnable) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(runnable);
        } catch (Throwable t) {
            LOG.error("UIUtils.postToUIThread error: " + t.getMessage());
        }
    }

    public static void postToUIThreadDelayed(Runnable runnable, long delayMillis) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(runnable, delayMillis);
        } catch (Throwable t) {
            LOG.error("UIUtils.postToUIThreadDelayed error: " + t.getMessage());
        }
    }

    public static void postToUIThreadAtFront(Runnable runnable) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postAtFrontOfQueue(runnable);
        } catch (Throwable t) {
            LOG.error("UIUtils.postToUIThreadAtFront error: " + t.getMessage());
        }
    }

    public static boolean isUIThread() {
        return Platforms.get().isUIThread();
    }

    public static boolean isAppInForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
            if (appProcesses == null) {
                return false;
            }
            for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        appProcess.processName.equals(context.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public enum HandlerThreadName {
        SEARCH_PERFORMER,
        DOWNLOADER,
        CONFIG_MANAGER,
        MISC
    }

    public static void postToHandler(final HandlerThreadName threadName, final Runnable r) {
        HandlerFactory.postTo(threadName, r);
    }

    @SuppressWarnings("unused")
    public static void postToHandlerDelayed(final HandlerThreadName threadName, final Runnable r, long delayMillis) {
        HandlerFactory.postDelayedTo(threadName, r, delayMillis);
    }

    public static void stopAllHandlerThreads() {
        HandlerFactory.stopAll();
    }

    private static class HandlerFactory {
        private static final HashMap<String, Handler> handlers = new HashMap<>();

        public static void postTo(final HandlerThreadName threadName, final Runnable r) {
            try {
                get(threadName.name()).post(r);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
        }

        public static void postDelayedTo(final HandlerThreadName threadName, final Runnable r, long delayMillis) {
            try {
                get(threadName.name()).postDelayed(r, delayMillis);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
        }

        private static Handler get(@NonNull final String threadName) {
            if (!handlers.containsKey(threadName)) {
                HandlerThread handlerThread = new HandlerThread("SystemUtils::HandlerThread - " + threadName);
                handlerThread.start();
                Handler handler = new Handler(handlerThread.getLooper());
                handlers.put(threadName, handler);
                return handler;
            }
            return handlers.get(threadName);
        }

        public static void stopAll() {
            try {
                handlers.values().forEach(handler -> ((HandlerThread) handler.getLooper().getThread()).quitSafely());
                handlers.clear();
            } catch (Throwable t) {
                LOG.error("HandlerFactory.stopAll() error " + t.getMessage(), t);
            }
        }
    }
}
