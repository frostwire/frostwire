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

package com.frostwire.concurrent.concurrent;

import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;

/**
 * A factory that builds {@link ExecutorService}, {@link ThreadFactory} and {@link
 * ScheduledExecutorService} objects via static methods.
 *
 * <p><code>ExecutorsHelper</code> differs from {@link Executors} since <code>ExecutorsHelper</code>
 * returns the thread factory for daemon threads and creates non-fixed size thread pools.
 * Additionally, <code>ExecutorsHelper</code> guarantees the returned <code>ExecutorService
 * </code> will allow worker threads to expire. On the other hand, <code>Executors</code> create
 * <code>ExecutorService</code>s whose core-pool of worker threads never die.
 */
public class ExecutorsHelper {
  /**
   * Creates a new "ProcessingQueue" using {@link #daemonThreadFactory(String)} as thread factory.
   *
   * <p>See {@link #newProcessingQueue(ThreadFactory)}.
   *
   * @param name the name of the processing thread that is created with the daemon thread factory.
   */
  public static ExecutorService newProcessingQueue(String name) {
    return newProcessingQueue(daemonThreadFactory(name));
  }

  /**
   * Creates a new "ProcessingQueue".
   *
   * <p>A <code>ProcessingQueue</code> is an <code>ExecutorService</code> that will process all
   * Runnables/Callables sequentially, creating one thread for processing when it needs it.
   *
   * <p>See {@link #newSingleThreadExecutor(ThreadFactory)}.
   *
   * @param factory the factory used for creating a new processing thread
   */
  public static ExecutorService newProcessingQueue(ThreadFactory factory) {
    return Executors.unconfigurableExecutorService(newSingleThreadExecutor(factory));
  }

  /**
   * A ProcessingQueue Executor is an <code>ExecutorService</code> that processes all
   * Runnables/Callables sequentially, creating one thread for processing when it needs it.
   *
   * <p>This kind of Executor is ideal for long-lived tasks that require processing rarely.
   *
   * <p>If there are no tasks the thread will be terminated after a timeout of 5 seconds and a new
   * one will be created when necessary.
   *
   * @param factory the factory used for creating a new processing thread
   */
  private static ThreadPoolExecutor newSingleThreadExecutor(ThreadFactory factory) {
    ThreadPoolExecutor tpe =
        new ThreadPoolExecutor(1, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), factory);
    tpe.allowCoreThreadTimeOut(true);
    return tpe;
  }

  /**
   * Creates the pool behind {@code ThreadExecutor}: one worker, started now and never timed out,
   * and an unbounded queue, so tasks run one at a time in submission order and {@code execute} only
   * enqueues. Callers on the EDT (a new search's table sort, dialogs) therefore never start a
   * thread: {@code Thread.start} waits on the JVM's thread list lock, held through GC safepoints,
   * and on the OS, and stalled the EDT for over 5 s (frostwire/frostwire#1314, #1332).
   *
   * <p>The pool used to declare 0 core and 256 maximum threads, but with an unbounded queue a
   * {@code ThreadPoolExecutor} never grows past its core size (or past one worker when that is 0):
   * it always ran one worker, which died after 5 idle seconds.
   *
   * @param factory the factory used for creating the processing thread
   */
  static ExecutorService newThreadPool(
      @SuppressWarnings("SameParameterValue") ThreadFactory factory) {
    ThreadPoolExecutor tpe =
        new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
    tpe.prestartCoreThread();
    return Executors.unconfigurableExecutorService(tpe);
  }

  /**
   * Creates a new ThreadPool with the maximum number of available threads. At most 4 items wait
   * while all threads are busy; further items are rejected with {@link
   * java.util.concurrent.RejectedExecutionException}. Use {@link
   * #newFixedSizeUnboundedThreadPool(int, String)} when callers cannot handle rejection.
   */
  public static ExecutorService newFixedSizeThreadPool(int size, String name) {
    ThreadPoolExecutor tpe =
        new ThreadPoolExecutor(
            size,
            size,
            5L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(4),
            daemonThreadFactory(name));
    tpe.allowCoreThreadTimeOut(true);
    return Executors.unconfigurableExecutorService(tpe);
  }

  /**
   * Creates a new ThreadPool with at most {@code size} threads whose waiting queue is unbounded:
   * items added while all threads are busy wait their turn instead of being rejected. Idle threads
   * time out after 5 seconds.
   */
  public static ExecutorService newFixedSizeUnboundedThreadPool(int size, String name) {
    ThreadPoolExecutor tpe =
        new ThreadPoolExecutor(
            size,
            size,
            5L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            daemonThreadFactory(name));
    tpe.allowCoreThreadTimeOut(true);
    return Executors.unconfigurableExecutorService(tpe);
  }

  /**
   * Creates a new ScheduledThreadPool with the specified number of threads.
   *
   * @param corePoolSize the number of threads to keep in the pool
   * @param name the name of the threads in the pool
   * @return a ScheduledExecutorService
   */
  public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, String name) {
    return Executors.unconfigurableScheduledExecutorService(
        new ScheduledThreadPoolExecutor(corePoolSize, daemonThreadFactory(name)));
  }

  /** Returns a thread factory of daemon threads, using the given name. */
  static ThreadFactory daemonThreadFactory(String name) {
    return new DefaultThreadFactory(name, true);
  }

  /** A thread factory that can create threads with a name. */
  private static class DefaultThreadFactory implements ThreadFactory {
    /** The name created threads will use. */
    private final String name;

    /** Whether or not the created thread is a daemon thread. */
    private final boolean daemon;

    /** Constructs a thread factory that will created named threads. */
    DefaultThreadFactory(String name, boolean daemon) {
      this.name = name;
      this.daemon = daemon;
    }

    public Thread newThread(@NotNull Runnable r) {
      Thread t = new ManagedThread(r, name);
      if (daemon) t.setDaemon(true);
      return t;
    }
  }
}
