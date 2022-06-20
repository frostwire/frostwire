/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

import java.io.File;
import java.util.HashMap;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SystemUtils {

    private static final Logger LOG = Logger.getLogger(SystemUtils.class);

    private static final int VERSION_SDK_NOUGAT_7_0_N = 24;
    private static final int VERSION_SDK_ANDROID_10_Q = 29;
    private static final int VERSION_SDK_ANDROID_11_R = 30;

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
     * Used to determine if the device is running Android11 or greater
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
                handler.post(() -> {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        LOG.error("safePost() " + t.getMessage(), t);
                    }
                });
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

    public static void postToUIThreadDelayed(Runnable runnable) {
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
            LOG.error("UIUtils.postToUIThread error: " + t.getMessage());
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
