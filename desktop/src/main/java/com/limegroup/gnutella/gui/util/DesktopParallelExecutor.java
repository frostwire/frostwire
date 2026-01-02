/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
 * Static helper class for parallel background task execution from the GUI.
 *
 * Use this for independent, parallelizable tasks (file I/O, network requests, search operations).
 * For sequential operations that must be executed serially, use {@link BackgroundQueuedExecutorService}.
 */
public class DesktopParallelExecutor {
    /**
     * Thread pool for parallel background execution.
     * 4 threads allows reasonable parallelism without excessive resource consumption.
     */
    private static final ExecutorService POOL = ExecutorsHelper.newFixedSizeThreadPool(4, "DesktopParallelExecutor");

    private DesktopParallelExecutor() {
    }

    /**
     * Executes the specified runnable in parallel when threads are available.
     *
     * @param r the runnable to execute
     */
    public static void execute(Runnable r) {
        POOL.execute(r);
    }

    /**
     * Submits a callable task for parallel execution and returns a Future for the result.
     *
     * @param c the callable to execute
     * @return a Future representing the pending result of the callable
     */
    public static <T> Future<T> submit(Callable<T> c) {
        return POOL.submit(c);
    }
}
