/*
 * File    : ThreadPool.java
 * Created : 21-Nov-2003
 * By      : parg
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.gudy.azureus2.core3.util;

import java.util.ArrayList;
import java.util.List;

//import org.gudy.azureus2.core3.config.COConfigurationManager;
//import org.gudy.azureus2.core3.config.ParameterListener;


public class
ThreadPool {
    private static final boolean NAME_THREADS = false;
    private static final boolean LOG_WARNINGS = false;
    private static final int WARN_TIME = 10000;

    private static final List busy_pools = new ArrayList();
    private static boolean busy_pool_timer_set = false;

    private static final ThreadLocal tls =
            ThreadLocal.withInitial(() -> (null));

    private static void
    checkAllTimeouts() {
        List pools;

        // copy the busy pools to avoid potential deadlock due to synchronization
        // nestings

        synchronized (busy_pools) {

            pools = new ArrayList(busy_pools);
        }

        for (Object pool : pools) {

            ((ThreadPool) pool).checkTimeouts();
        }
    }


    private String name;
    private int max_size;
    private int thread_name_index = 1;

    private List busy;
    private boolean queue_when_full;
    private final List task_queue = new ArrayList();

    private AESemaphore thread_sem;
    private int reserved_target;
    private int reserved_actual;

    private boolean warn_when_full;

    private long task_total;
    private long task_total_last;
    private final Average task_average = Average.getInstance(WARN_TIME, 120);

    public ThreadPool(
            String _name,
            int _max_size) {
        this(_name, _max_size, false);
    }

    private ThreadPool(
            String _name,
            int _max_size,
            boolean _queue_when_full) {
        name = _name;
        max_size = _max_size;
        queue_when_full = _queue_when_full;

        thread_sem = new AESemaphore(_max_size);

        busy = new ArrayList(_max_size);
    }

    void
    setWarnWhenFull() {
        warn_when_full = true;
    }

    public void run(AERunnable runnable) {
        run(runnable, false, false);
    }


    private threadPoolWorker run(AERunnable runnable, boolean high_priority, boolean manualRelease) {

        if (manualRelease && !(runnable instanceof ThreadPoolTask))
            throw new IllegalArgumentException("manual release only allowed for ThreadPoolTasks");
        else if (manualRelease)
            ((ThreadPoolTask) runnable).setManualRelease();

        // System.out.println( "Thread pool:" + name + " - sem = " + thread_sem.getValue() + ", queue = " + task_queue.size());

        // not queueing, grab synchronous sem here

        if (!queue_when_full) {

            if (!thread_sem.reserveIfAvailable()) {

                // defend against recursive entry when in queuing mode (yes, it happens)

                threadPoolWorker recursive_worker = (threadPoolWorker) tls.get();

                if (recursive_worker == null || recursive_worker.getOwner() != this) {

                    // do a blocking reserve here, not recursive

                    checkWarning();

                    thread_sem.reserve();

                } else {
                    // run immediately

                    if (runnable instanceof ThreadPoolTask) {

                        ThreadPoolTask task = (ThreadPoolTask) runnable;

                        task.worker = recursive_worker;

                        runIt(runnable);
                        task.join();
                    } else {

                        runIt(runnable);
                    }

                    return (recursive_worker);
                }
            }
        }

        threadPoolWorker allocated_worker;

        synchronized (this) {

            if (high_priority)
                task_queue.add(0, runnable);
            else
                task_queue.add(runnable);

            // reserve if available is non-blocking

            if (queue_when_full && !thread_sem.reserveIfAvailable()) {

                allocated_worker = null;

                checkWarning();

            } else {

                allocated_worker = new threadPoolWorker();

            }
        }

        return (allocated_worker);
    }

    private void runIt(AERunnable runnable) {
        runnable.run();
    }

    private void checkWarning() {
        if (warn_when_full) {
            StringBuilder task_names = new StringBuilder();
            try {
                synchronized (ThreadPool.this) {
                    for (Object o : busy) {
                        threadPoolWorker x = (threadPoolWorker) o;
                        AERunnable r = x.runnable;
                        if (r != null) {
                            String name;
                            if (r instanceof ThreadPoolTask)
                                name = ((ThreadPoolTask) r).getName();
                            else
                                name = r.getClass().getName();
                            task_names.append(task_names.length() == 0 ? "" : ",").append(name);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            System.out.println("Thread pool '" + getName() + "' is full (busy=" + task_names + ")");
            warn_when_full = false;
        }
    }


    private void
    checkTimeouts() {
        synchronized (this) {

            long diff = task_total - task_total_last;

            task_average.addValue(diff);

            task_total_last = task_total;


            long now = SystemTime.getMonotonousTime();

            for (Object o : busy) {

                threadPoolWorker x = (threadPoolWorker) o;

                long elapsed = now - x.run_start_time;

                if (elapsed > ((long) WARN_TIME * (x.warn_count + 1))) {
                    x.warn_count++;

                }
            }
        }
    }

    public String getName() {
        return (name);
    }

    void releaseManual(ThreadPoolTask toRelease) {
        if (!toRelease.canManualRelease()) {
            throw new IllegalStateException("task not manually releasable");
        }

        synchronized (this) {

            long elapsed = SystemTime.getMonotonousTime() - toRelease.worker.run_start_time;
            if (elapsed > WARN_TIME && LOG_WARNINGS)
                System.out.println(toRelease.worker.getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + toRelease.worker.state);

            if (!busy.remove(toRelease.worker)) {

                throw new IllegalStateException("task already released");
            }

            // if debug is on we leave the pool registered so that we
            // can trace on the timeout events

            if (busy.size() == 0) {

                synchronized (busy_pools) {

                    busy_pools.remove(this);
                }
            }

            if (busy.size() == 0) {

                if (reserved_target > reserved_actual) {

                    reserved_actual++;

                } else {

                    thread_sem.release();
                }
            } else {

                new threadPoolWorker();
            }
        }

    }


    class threadPoolWorker extends AEThread2 {
        private final String worker_name;
        private volatile AERunnable runnable;
        private long run_start_time;
        private int warn_count;
        private String state = "<none>";

        threadPoolWorker() {
            super(NAME_THREADS ? (name + " " + (thread_name_index)) : name, true);
            thread_name_index++;
            int thread_priority = Thread.NORM_PRIORITY;
            setPriority(thread_priority);
            worker_name = this.getName();
            start();
        }

        public void run() {
            tls.set(threadPoolWorker.this);

            boolean autoRelease = true;

            try {
                do {
                    try {
                        synchronized (ThreadPool.this) {
                            if (task_queue.size() > 0)
                                runnable = (AERunnable) task_queue.remove(0);
                            else
                                break;
                        }

                        synchronized (ThreadPool.this) {
                            run_start_time = SystemTime.getMonotonousTime();
                            warn_count = 0;
                            busy.add(threadPoolWorker.this);
                            task_total++;
                            if (busy.size() == 1) {
                                synchronized (busy_pools) {
                                    if (!busy_pools.contains(ThreadPool.this)) {
                                        busy_pools.add(ThreadPool.this);
                                        if (!busy_pool_timer_set) {
                                            // we have to defer this action rather
                                            // than running as a static initialiser
                                            // due to the dependency between
                                            // ThreadPool, Timer and ThreadPool again
//											COConfigurationManager.addAndFireParameterListeners(new String[] { "debug.threadpool.log.enable", "debug.threadpool.debug.trace" }, new ParameterListener()
//											{
//												public void parameterChanged(String name) {
//													debug_thread_pool = COConfigurationManager.getBooleanParameter("debug.threadpool.log.enable", false);
//													debug_thread_pool_log_on = COConfigurationManager.getBooleanParameter("debug.threadpool.debug.trace", false);
//												}
//											});
                                            busy_pool_timer_set = true;
                                            SimpleTimer.addPeriodicEvent("ThreadPool:timeout", WARN_TIME, event -> checkAllTimeouts());
                                        }
                                    }
                                }
                            }
                        }

                        if (runnable instanceof ThreadPoolTask) {
                            ThreadPoolTask tpt = (ThreadPoolTask) runnable;
                            tpt.worker = this;
                            String task_name = NAME_THREADS ? tpt.getName() : null;
                            try {
                                if (task_name != null)
                                    setName(worker_name + "{" + task_name + "}");

                                runIt(runnable);
                            } finally {
                                if (task_name != null)
                                    setName(worker_name);

                                if (!tpt.isAutoReleaseAndAllowManual()) {
                                    autoRelease = false;
                                    break;
                                }
                            }
                        } else
                            runIt(runnable);

                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        if (autoRelease) {
                            synchronized (ThreadPool.this) {
                                long elapsed = SystemTime.getMonotonousTime() - run_start_time;
                                if (elapsed > WARN_TIME && LOG_WARNINGS)
                                    System.out.println(getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + state);

                                busy.remove(threadPoolWorker.this);

                                // if debug is on we leave the pool registered so that we
                                // can trace on the timeout events
                                if (busy.size() == 0)
                                    synchronized (busy_pools) {
                                        busy_pools.remove(ThreadPool.this);
                                    }
                            }
                        }
                    }
                } while (runnable != null);
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                if (autoRelease) {

                    synchronized (ThreadPool.this) {

                        if (reserved_target > reserved_actual) {

                            reserved_actual++;

                        } else {

                            thread_sem.release();
                        }
                    }
                }

                tls.set(null);
            }
        }

        public void setState(String _state) {
            //System.out.println( "state = " + _state );
            state = _state;
        }

        public String getState() {
            return (state);
        }

        String getWorkerName() {
            return (worker_name);
        }

        ThreadPool getOwner() {
            return (ThreadPool.this);
        }
    }
}
