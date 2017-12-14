/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

import android.os.SystemClock;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 12/14/17.
 */


public class StopClock {
    private static StopClock instance = new StopClock();
    long start;
    private StopClock() {
        start = -1;
    }

    public static void start() {
        instance.start = SystemClock.currentThreadTimeMillis();
    }

    public static void stop(String methodName) {
        if (instance.start != -1) {
            long duration = SystemClock.currentThreadTimeMillis() - instance.start;
            System.out.println("[StopClock]: " + methodName + " finished in " + duration + " ms");
        }
        instance.start = -1;
    }
}
