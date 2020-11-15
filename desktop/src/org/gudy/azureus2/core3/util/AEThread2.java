/*
 * Created on Nov 9, 2007
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package org.gudy.azureus2.core3.util;

import java.util.LinkedList;

abstract class AEThread2 {
    private static final int MIN_RETAINED = Math.max(Runtime.getRuntime().availableProcessors(), 2);
    private static final int MAX_RETAINED = Math.max(MIN_RETAINED * 4, 16);
    private static final int THREAD_TIMEOUT_CHECK_PERIOD = 10 * 1000;
    private static final int THREAD_TIMEOUT = 60 * 1000;
    private static final LinkedList<threadWrapper> daemon_threads = new LinkedList<>();
    private static long last_timeout_check;
    private final boolean daemon;
    private threadWrapper wrapper;
    private String name;
    private int priority = Thread.NORM_PRIORITY;
    private volatile JoinLock lock = new JoinLock();
    AEThread2(String _name, boolean _daemon) {
        name = _name;
        daemon = _daemon;
    }

    /**
     * multiple invocations of start() are possible, but discouraged if combined
     * with other thread operations such as interrupt() or join()
     */
    void
    start() {
        synchronized (lock) {
            if (lock.released)
                lock = new JoinLock();
        }
        if (daemon) {
            synchronized (daemon_threads) {
                if (daemon_threads.isEmpty()) {
                    wrapper = new threadWrapper(name, true);
                } else {
                    wrapper = (threadWrapper) daemon_threads.removeLast();
                    wrapper.setName(name);
                }
            }
        } else {
            wrapper = new threadWrapper(name, false);
        }
        if (priority != wrapper.getPriority()) {
            wrapper.setPriority(priority);
        }
        wrapper.currentLock = lock;
        wrapper.start(this, name);
    }

    void
    setPriority(
            int _priority) {
        priority = _priority;
        if (wrapper != null) {
            wrapper.setPriority(priority);
        }
    }

    String
    getName() {
        return (name);
    }

    void
    setName(
            String s) {
        name = s;
        if (wrapper != null) {
            wrapper.setName(name);
        }
    }

    public String
    toString() {
        if (wrapper == null) {
            return (name + " [daemon=" + daemon + ",priority=" + priority + "]");
        } else {
            return (wrapper.toString());
        }
    }

    protected abstract void
    run();

    private static final class JoinLock {
        volatile boolean released = false;
    }

    static class
    threadWrapper
            extends Thread {
        private AESemaphore2 sem;
        private AEThread2 target;
        private JoinLock currentLock;
        private long last_active_time;

        @SuppressWarnings("unused")
        threadWrapper(
                String name,
                boolean daemon) {
            super(name);
            setDaemon(daemon);
        }

        public void
        run() {
            while (true) {
                synchronized (currentLock) {
                    try {
                        target.run();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        target = null;
                        currentLock.released = true;
                        currentLock.notifyAll();
                    }
                }
                if (isInterrupted() || !Thread.currentThread().isDaemon()) {
                    break;
                } else {
                    synchronized (daemon_threads) {
                        last_active_time = SystemTime.getCurrentTime();
                        if (last_active_time < last_timeout_check ||
                                last_active_time - last_timeout_check > THREAD_TIMEOUT_CHECK_PERIOD) {
                            last_timeout_check = last_active_time;
                            while (daemon_threads.size() > 0 && daemon_threads.size() > MIN_RETAINED) {
                                threadWrapper thread = (threadWrapper) daemon_threads.getFirst();
                                long thread_time = thread.last_active_time;
                                if (last_active_time < thread_time ||
                                        last_active_time - thread_time > THREAD_TIMEOUT) {
                                    daemon_threads.removeFirst();
                                    thread.retire();
                                } else {
                                    break;
                                }
                            }
                        }
                        if (daemon_threads.size() >= MAX_RETAINED) {
                            return;
                        }
                        daemon_threads.addLast(this);
                        setName("AEThread2:parked[" + daemon_threads.size() + "]");
                        // System.out.println( "AEThread2: queue=" + daemon_threads.size() + ",creates=" + total_creates + ",starts=" + total_starts );
                    }
                    sem.reserve();
                    if (target == null) {
                        break;
                    }
                }
            }
        }

        void
        start(
                AEThread2 _target,
                String _name) {
            target = _target;
            setName(_name);
            if (sem == null) {
                sem = new AESemaphore2();
                super.start();
            } else {
                sem.release();
            }
        }

        void
        retire() {
            sem.release();
        }
    }
}
