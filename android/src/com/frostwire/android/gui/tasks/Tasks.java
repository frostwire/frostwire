/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.tasks;

import android.os.AsyncTask;
import android.os.Build;
import com.frostwire.logging.Logger;

/**
 * Utility class to have AsyncTasks executed in the proper executor.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Tasks {
    private static Logger LOG = Logger.getLogger(Tasks.class);

    public static <Param, Progress, Report> void executeSerial(
            AsyncTask<Param, Progress, Report> task,
            Param... args) {
        execute(true, task, args);
    }

    public static <Param, Progress, Report> void executeParallel(
            AsyncTask<Param, Progress, Report> task,
            Param... args) {
        execute(false, task, args);
    }

    private static <Param> void execute(
            boolean serial,
            AsyncTask<Param, ?, ?> task,
            Param... args) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.DONUT) {
            LOG.warn("This class can only be used on API 4 and newer.");
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB || serial) {
            // Same as task.execute() but let's just be sure
            task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, args);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, args);
        }
    }
}
