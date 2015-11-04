/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 
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

package com.frostwire.android.tests;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.Debug;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class TestUtils {

    private TestUtils() {
    }

    public static boolean await(CountDownLatch signal, long timeout, TimeUnit unit) {
        try {
            return signal.await(timeout, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public static long getPss() {
        System.gc();
        try {
            Method m = Debug.class.getDeclaredMethod("getPss");
            return (Long) m.invoke(null);
        } catch (Throwable e) {
            throw new RuntimeException("Unable to run test on this device", e);
        }
    }

    public static void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
        }
    }
}
