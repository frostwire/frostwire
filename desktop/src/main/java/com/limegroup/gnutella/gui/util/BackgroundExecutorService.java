/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.util;

import com.frostwire.concurrent.concurrent.ExecutorsHelper;

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
    private static final ExecutorService QUEUE = ExecutorsHelper.newProcessingQueue("BackgroundExecutorService");

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
