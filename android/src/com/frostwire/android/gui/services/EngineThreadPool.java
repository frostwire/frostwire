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

import java.util.Map;
import java.util.WeakHashMap;
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

    // look at AsyncTask for a more dynamic calculation, but it yields
    // 17 in a medium hardware phone
    private static final int MAXIMUM_POOL_SIZE = 4;

    private final WeakHashMap<Object, String> taskStack;
    private final WeakHashMap<Thread, TaskInfo> taskInfo;

    EngineThreadPool() {
        super("Engine", MAXIMUM_POOL_SIZE, new LinkedBlockingQueue<>(), false);

        taskStack = new WeakHashMap<>();
        taskInfo = new WeakHashMap<>();
    }

    @Override
    public void execute(Runnable command) {
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

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        if (Debug.isEnabled()) {
            String stack = taskStack.get(r);
            if (stack != null) {
                taskInfo.put(t, new TaskInfo(System.nanoTime(), stack));
            }
        }

        super.beforeExecute(t, r);
    }

    private void verifyTask(Object task) {
        if (Debug.hasContext(task)) {
            throw new RuntimeException("Runnable/task contains context, possible context leak");
        }

        if (Debug.isEnabled()) {
            taskStack.put(task, getStack());
        }

        // if debug/development, allow only 20 tasks in the queue
        if (Debug.isEnabled() && getQueue().size() > 20) {
            dumpTasks();
            throw new RuntimeException("Too many tasks in the queue");
        }
    }

    private void dumpTasks() {
        System.out.println("Running threads in engine pool");
        long now = System.nanoTime();
        for (Map.Entry<Thread, TaskInfo> e : taskInfo.entrySet()) {
            String threadName = e.getKey().getName();
            if (threadName != null && threadName.contains("thread-idle")) {
                continue;
            }
            System.out.println("Thread name: " + threadName);
            System.out.println("\tTime running: " + ((now - e.getValue().time) / 1000000) + "ms");
            System.out.println("\tStack trace:");
            System.out.println(e.getValue().stack);
        }
    }

    private static String getStack() {
        StackTraceElement[] callStack = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 5; i < callStack.length; i++) {
            StackTraceElement caller = callStack[i];
            sb.append("\t\t at ");
            sb.append(caller.getClassName()).append(".").append(caller.getMethodName());

            String fileName = caller.getFileName();
            int lineNumber = caller.getLineNumber();
            if (fileName != null) {
                sb.append("(").append(fileName);
                if (lineNumber > 0) {
                    sb.append(":").append(lineNumber);
                }
                sb.append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static final class TaskInfo {

        final long time;
        final String stack;

        TaskInfo(long time, String stack) {
            this.time = time;
            this.stack = stack;
        }
    }
}
