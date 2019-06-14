/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.util;

import org.limewire.concurrent.ExecutorsHelper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Static helper class that allows background tasks to be scheduled from the GUI.
 */
public class BackgroundExecutorService {
    /**
     * Queue for items to be run in the background.
     */
    private static final ExecutorService QUEUE = ExecutorsHelper.newProcessingQueue(" ");

    private BackgroundExecutorService() {
    }

    /**
     * Runs the specified runnable in a different thread when it can.
     */
    public static void schedule(Runnable r) {
        QUEUE.execute(r);
    }

    @SuppressWarnings("unused")
    public static <T> Future<T> submit(Callable<T> c) {
        return QUEUE.submit(c);
    }
}
