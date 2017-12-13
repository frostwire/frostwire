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

import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.os.StrictMode;
import android.view.View;

import com.frostwire.android.BuildConfig;
import com.frostwire.util.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * Utility class for runtime debugging.
 * <p>
 * None of the methods perform any operation is debug is not enabled.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Debug {

    private static final Logger LOG = Logger.getLogger(Debug.class);

    private Debug() {
    }

    /**
     * Returns if the application is running under a debug configuration.
     * <p>
     * The current implementation delegates the check to the native
     * android {@code BuildConfig} but that's not necessary the only way.
     *
     * @return {@code true} if running under debug
     */
    public static boolean isEnable() {
        return BuildConfig.DEBUG;
    }

    /**
     * Enable the most strict form of {@link StrictMode} possible,
     * with log and death as penalty. When {@code enable} is {@code false}, the
     * default more relaxed (LAX) policy is used.
     * <p>
     * This method only perform an actual action if the application is
     * in debug mode.
     *
     * @param enable {@code true} activate the most strict policy
     */
    public static void setStrictPolicy(boolean enable) {
        if (!isEnable()) {
            return; // no debug mode, do nothing
        }

        // by default, the LAX policy
        StrictMode.ThreadPolicy threadPolicy = StrictMode.ThreadPolicy.LAX;
        StrictMode.VmPolicy vmPolicy = StrictMode.VmPolicy.LAX;

        if (enable) {
            threadPolicy = new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build();
            vmPolicy = new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build();
        }

        StrictMode.setThreadPolicy(threadPolicy);
        StrictMode.setVmPolicy(vmPolicy);
    }

    /**
     * Runs the runnable code under strict policy.
     *
     * @param r the runnable to execute r.run()
     */
    public static void runStrict(Runnable r) {
        try {
            setStrictPolicy(true);
            r.run();
        } finally {
            setStrictPolicy(false);
        }
    }

    /**
     * Detects if any field/property owned by object can potentially
     * pin a context to memory.
     * <p>
     * This check can be used in places where some task is sent to
     * a background with a hard reference to a context, creating a
     * possible context leak.
     *
     * @param obj the object to inspect
     * @return {@code true} if the object can have a hard reference
     * to a context, {@code false} otherwise.
     */
    public static boolean hasContext(Object obj) {
        try {
            return hasContext(obj, 0);
        } catch (IllegalStateException e) {
            // don't just rethrow to flatten the stack information
            LOG.warn("hasContext() -> " + e.getMessage(), e);
            throw new IllegalStateException(e.getMessage() + ", class=" + obj.getClass().getName());
        }
    }

    private static boolean hasContext(Object obj, int level) {
        if (!isEnable()) {
            return false;
        }

        if (level > 200) {
            throw new IllegalStateException(
                    "Too much recursion in hasContext, flatten your objects");
        }

        try {
            if (hasNoContext(obj)) {
                return false;
            }

            Class<?> clazz = obj.getClass();
            Field[] fields = clazz.getDeclaredFields();

            // BFS variation algorithm

            for (Field f : fields) {
                f.setAccessible(true); // let's hope java 9 ideas don't get inside android
                Object value = f.get(obj);

                if (value instanceof Context) {
                    return true;
                }
                if (value instanceof Fragment) {
                    return true;
                }
                if (value instanceof View) {
                    return true;
                }
                if (value instanceof Dialog) {
                    return true;
                }
            }

            for (Field f : fields) {
                f.setAccessible(true);
                Object value = f.get(obj);

                // avoid recursion due to self reference field
                if (value == obj) {
                    continue;
                }

                if (hasContext(value, level + 1)) {
                    return true;
                }
            }

            return false;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            // in case of a fatal error, this is just a runtime
            // check under debug, just let it run
            return false;
        }
    }

    private static boolean hasNoContext(Object obj) {
        if (obj == null) {
            return true;
        } else if (obj instanceof WeakReference<?>) {
            return true;
        } else if (obj instanceof Number) {
            return true;
        } else if (obj instanceof String) {
            return true;
        } else if (obj instanceof Enum) {
            // avoids infinite recursion checking $VALUES field
            return true;
        } else if (obj instanceof Boolean) {
            return true;
        }

        // exclude some well know packages
        String clazzName = obj.getClass().getName();
        if (clazzName.startsWith("java.")) {
            return true;
        } else if (clazzName.startsWith("javax.")) {
            return true;
        } else if (clazzName.startsWith("com.sun.")) {
            return true;
        } else if (clazzName.startsWith("com.frostwire.search.")) {
            return true;
        } else if (clazzName.startsWith("com.frostwire.bittorrent.")) {
            return true;
        }

        return false;
    }
}
