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

package com.frostwire.android.gui.services;

import com.frostwire.android.util.Debug;
import com.frostwire.util.ThreadPool;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This executor helps to keep track of potential context leaks
 * and excessive submission of tasks.
 *
 * @author gubatron
 * @author aldenml
 */
final class EngineThreadPool extends ThreadPool {
    // private Logger LOG = Logger.getLogger(EngineThreadPool.class);
    // look at AsyncTask for a more dynamic calculation, but it yields
    // 17 in a medium hardware phone
    private static final int MAXIMUM_POOL_SIZE = 4;

    EngineThreadPool() {
        super("Engine", MAXIMUM_POOL_SIZE, new LinkedBlockingQueue<>(), false);
    }

    @Override
    public void execute(Runnable command) {
        //LOG.info("execute (tasks:"+getQueue().size()+") invoked from " + Debug.getCallingMethodInfo());
        verifyTask(command);
        super.execute(command);
    }

    @Override
    public Future<?> submit(Runnable task) {
        verifyTask(task);
        return super.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        verifyTask(task);
        return super.submit(task, result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        verifyTask(task);
        return super.submit(task);
    }

    private void verifyTask(Object task) {
        if (Debug.hasContext(task)) {
            throw new RuntimeException("Runnable/task contains context, possible context leak");
        }

        // if debug/development, allow only 20 tasks in the queue
        if (Debug.isEnable() && getQueue().size() > 20) {
            throw new RuntimeException("Too many tasks in the queue");
        }
    }
}
