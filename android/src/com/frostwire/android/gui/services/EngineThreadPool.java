/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.services;

import android.support.compat.BuildConfig;

import com.frostwire.android.util.Debug;
import com.frostwire.util.ThreadPool;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * This executor helps to keep track of potential context leaks
 * and excessive submission of tasks.
 *
 * @author gubatron
 * @author aldenml
 */
final class EngineThreadPool extends ThreadPool {

    // look at AsyncTask for a more dynamic calculation, but it yields
    // 17 in a medium hardware phone
    private static final int MAXIMUM_POOL_SIZE = 4;

    EngineThreadPool() {
        super("Engine", MAXIMUM_POOL_SIZE, new LinkedBlockingQueue<Runnable>(), false);
    }

    @Override
    public void execute(Runnable command) {
        if (Debug.hasContext(command)) {
            // TODO: uncomment after excessive submission is solved
            //throw new RuntimeException("Runnable contains context, possible context leak");
        }

        // if debug/development, allow only 20 tasks in the queue
        if (Debug.isEnable() && getQueue().size() > 20) {
            throw new RuntimeException("Too many tasks in the queue");
        }

        super.execute(command);
    }
}
