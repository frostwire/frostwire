/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

import android.app.Application;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Paint;
import android.view.View;

import com.frostwire.android.BuildConfig;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility class for runtime debugging.
 * <p>
 * None of the methods perform any operation if debug is not enabled.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Debug {

    private Debug() {
    }

    /**
     * Returns if the application is running under a debug configuration.
     * <p>
     * The current implementation delegates the check to the native
     * android {@code BuildConfig} but that's not necessary the only way.
     *
     * @return {@code true} if running as a debug build but disconnected from the debugger
     */
    public static boolean isEnabled() {
        return BuildConfig.DEBUG;
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
            return hasContext(obj, 0, new TreeSet<>());
        } catch (IllegalStateException e) {
            // don't just rethrow to flatten the stack information
            throw new IllegalStateException(e.getMessage() + ", class=" + obj.getClass().getName());
        }
    }

    private static boolean hasContext(Object obj, int level, Set<Integer> refs) {
        if (!isEnabled()) {
            return false;
        }

        if (obj == null) {
            return false;
        }

        if (level > 200) {
            throw new IllegalStateException(
                "Too much recursion in hasContext, flatten your objects, last object class is " +
                obj.getClass().getSimpleName());
        }

        refs.add(obj.hashCode());

        try {
            if (hasNoContext(obj)) {
                return false;
            }

            List<Field> fields = getAllFields(obj);

            // BFS variation algorithm

            for (Field f : fields) {
                f.setAccessible(true); // let's hope java 9 ideas don't get inside android
                Object value = f.get(obj);
                if (hasNoContext(value)) {
                    continue;
                }
                if (value instanceof Context ||
                    value instanceof Fragment ||
                    value instanceof View ||
                    value instanceof Dialog) {
                    System.out.println("Leakable obj " + f.getName() + " instance of " + value.getClass().getCanonicalName());
                    return true;
                }
            }

            for (Field f : fields) {
                f.setAccessible(true);
                Object value = f.get(obj);
                if (value == null) {
                    continue;
                }

                // avoid recursion due to a self reference value
                Integer h = value.hashCode();
                if (refs.contains(h)) {
                    continue;
                }

                if (hasContext(value, level + 1, refs)) {
                    return true;
                }
            }

            return false;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            // look out for changes in Android P
            // https://developer.android.com/preview/restrictions-non-sdk-interfaces.html
            // #ramifications_of_keeping_non-sdk_interfaces
            throw new RuntimeException(e);
        }
    }

    private static boolean hasNoContext(Object obj) {
        if (obj == null ||
            obj instanceof WeakReference<?> ||
            obj instanceof Application || // application is an application context
            obj instanceof Number ||
            obj instanceof String ||
            obj instanceof Enum || // avoids infinite recursion checking $VALUES field
            obj instanceof Boolean ||
            obj instanceof Paint) {
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
        } else if (clazzName.startsWith("android.util.")) {
            return true;
        } else if (clazzName.startsWith("dalvik.")) {
            return true;
        } else if (clazzName.startsWith("com.frostwire.search.")) {
            return true;
        } else if (clazzName.startsWith("com.frostwire.bittorrent.")) {
            return true;
        }

        // exclude some classes by name due to API level
        return clazzName.startsWith("android.os.LocaleList");

    }

    private static List<Field> getAllFields(Object obj) {
        List<Field> fields = new LinkedList<>();
        Class<?> clazz = obj.getClass();
        while (clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

}
