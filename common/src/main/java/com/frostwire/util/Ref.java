/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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


package com.frostwire.util;

import java.lang.ref.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Ref {
    private Ref() {
    }

    public static <T> T strong(T obj) {
        return obj;
    }

    public static <T> WeakReference<T> weak(T obj) {
        return new WeakReference<T>(obj);
    }

    public static <T> SoftReference<T> soft(T obj) {
        return new SoftReference<T>(obj);
    }

    public static <T> PhantomReference<T> phantom(T obj, ReferenceQueue<? super T> q) {
        return new PhantomReference<T>(obj, q);
    }

    public static <T> boolean alive(Reference<T> ref) {
        return ref != null && ref.get() != null;
    }

    public static void free(Reference<?> ref) {
        try {
            if (ref != null) {
                ref.clear();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
