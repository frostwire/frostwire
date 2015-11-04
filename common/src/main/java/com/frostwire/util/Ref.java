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

package com.frostwire.util;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
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
        ref.clear();
    }
}
