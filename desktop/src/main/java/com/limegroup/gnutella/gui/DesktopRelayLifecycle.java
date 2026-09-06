/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.LifecycleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** One terminal desktop relay generation; startup and disposal never overlap. */
final class DesktopRelayLifecycle implements BooleanSupplier, AutoCloseable {
  private static final Logger LOG = Logger.getLogger(DesktopRelayLifecycle.class);
  private static final long DRAIN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2);
  private static final long RETRY_DELAY_MILLIS = 250;

  private final AtomicBoolean closed = new AtomicBoolean();
  private final CountDownLatch disposed = new CountDownLatch(1);
  private final List<Worker> workers = new ArrayList<>();
  private final Runnable releaseResources;
  private final Runnable disposeStore;
  // close submits once; each failed drain replaces itself with one delayed retry.
  private final ScheduledExecutorService cleanup =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "desktop-relay-cleanup");
            thread.setDaemon(true);
            return thread;
          });
  private boolean started;
  private boolean starting;
  private boolean resourcesReleased;

  DesktopRelayLifecycle(Runnable releaseResources, Runnable disposeStore) {
    this.releaseResources = Objects.requireNonNull(releaseResources, "releaseResources");
    this.disposeStore = Objects.requireNonNull(disposeStore, "disposeStore");
  }

  @Override
  public boolean getAsBoolean() {
    return !closed.get();
  }

  synchronized boolean beginStartup() {
    if (closed.get() || started) {
      return false;
    }
    started = true;
    starting = true;
    return true;
  }

  /** Register the drain barrier before borrowing the core's native session. */
  synchronized boolean beginStartup(LifecycleManager manager) {
    if (closed.get() || started) {
      return false;
    }
    Thread drain =
        new Thread(
            () -> {
              close();
              try {
                while (!awaitDisposed(1, TimeUnit.SECONDS)) {
                  // Normal termination promises disposal; a timed-out wait cannot release native
                  // ownership.
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Relay shutdown interrupted before disposal", e);
              }
            },
            "relay-core-shutdown");
    drain.setDaemon(true);
    try {
      manager.addShutdownItem(drain);
    } catch (RuntimeException e) {
      close();
      throw e;
    }
    return beginStartup();
  }

  synchronized void finishStartup() {
    starting = false;
  }

  /** Register only the four non-waiting common lifecycle owners, during startup. */
  void own(AutoCloseable resource, StoppedAwaiter awaiter) {
    Worker worker =
        new Worker(
            Objects.requireNonNull(resource, "resource"),
            Objects.requireNonNull(awaiter, "awaiter"));
    synchronized (this) {
      if (!starting) {
        throw new IllegalStateException("Relay resource registered outside startup");
      }
      workers.add(worker);
    }
    // Stop may have raced construction/registration. No new work is permitted in either case.
    if (closed.get()) {
      closeWorker(worker);
    }
  }

  /** Revokes before dispatching cleanup, without waiting for startup, providers or disk. */
  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    List<Worker> snapshot;
    synchronized (this) {
      snapshot = new ArrayList<>(workers);
    }
    for (Worker worker : snapshot) {
      closeWorker(worker);
    }
    cleanup.execute(this::drainAndDispose);
  }

  /** Bounded worker/shutdown-hook wait, never an EDT join or a native-completion guarantee. */
  boolean awaitDisposed(long timeout, TimeUnit unit) throws InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("Relay disposal must not be awaited on the EDT");
    }
    if (timeout < 0) {
      throw new IllegalArgumentException("timeout must be >= 0");
    }
    return disposed.await(timeout, unit);
  }

  private void drainAndDispose() {
    boolean complete = false;
    try {
      List<Worker> snapshot;
      synchronized (this) {
        if (starting) {
          return;
        }
        snapshot = new ArrayList<>(workers);
      }
      if (!resourcesReleased) {
        releaseResources.run();
        resourcesReleased = true;
      }
      long deadline = System.nanoTime() + DRAIN_TIMEOUT_NANOS;
      for (Worker worker : snapshot) {
        if (!worker.awaiter.awaitStopped(
            Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
          return; // The store is still borrowed. Retain this owner until a later drain succeeds.
        }
      }
      disposeStore.run();
      complete = true;
      synchronized (this) {
        workers.clear();
      }
      disposed.countDown();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      LOG.warn("Relay cleanup deferred; borrowed resources retained", t);
    } finally {
      if (complete) {
        cleanup.shutdown();
      } else {
        cleanup.schedule(this::drainAndDispose, RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
      }
    }
  }

  private static void closeWorker(Worker worker) {
    try {
      worker.resource.close();
    } catch (Exception e) {
      LOG.warn("Failed to revoke relay worker", e);
    }
  }

  @FunctionalInterface
  interface StoppedAwaiter {
    boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException;
  }

  private record Worker(AutoCloseable resource, StoppedAwaiter awaiter) {}
}
