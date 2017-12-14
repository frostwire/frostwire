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


public class StopWatch {
    long start;
    public StopWatch() {
        start = -1;
    }

    public void start() {
        start = SystemClock.currentThreadTimeMillis();
    }

    public void stop(String methodName) {
        if (start != -1) {
            long duration = SystemClock.currentThreadTimeMillis() - start;
            System.out.println("[StopWatch]: " + methodName + " finished in " + duration + " ms");
        }
        start = -1;
    }
}
