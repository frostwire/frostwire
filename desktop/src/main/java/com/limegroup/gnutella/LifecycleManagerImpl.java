package com.limegroup.gnutella;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.concurrent.concurrent.ThreadExecutor;
import com.frostwire.service.ErrorService;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.limewire.setting.SettingsGroupManager;

public class LifecycleManagerImpl implements LifecycleManager {
  private static final Logger LOG = Logger.getLogger(LifecycleManagerImpl.class);
  private static final long SHUTDOWN_ITEMS_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
  private final AtomicBoolean preinitializeBegin = new AtomicBoolean(false);
  private final AtomicBoolean preinitializeDone = new AtomicBoolean(false);
  private final AtomicBoolean backgroundBegin = new AtomicBoolean(false);
  private final AtomicBoolean backgroundDone = new AtomicBoolean(false);
  private final AtomicBoolean startBegin = new AtomicBoolean(false);
  private final AtomicBoolean startDone = new AtomicBoolean(false);
  private final AtomicBoolean shutdownBegin = new AtomicBoolean(false);
  private final AtomicBoolean shutdownDone = new AtomicBoolean(false);
  private final CountDownLatch startLatch = new CountDownLatch(1);
  private final LimeCoreGlue limeCoreGlue;
  private final Runnable stopEngine;

  /** A list of items that require running prior to shutting down LW. */
  private final List<Thread> SHUTDOWN_ITEMS = new LinkedList<>();

  /**/
  LifecycleManagerImpl(LimeCoreGlue limeCoreGlue) {
    this(limeCoreGlue, () -> BTEngine.getInstance().stop());
  }

  LifecycleManagerImpl(LimeCoreGlue limeCoreGlue, Runnable stopEngine) {
    this.limeCoreGlue = limeCoreGlue;
    this.stopEngine = Objects.requireNonNull(stopEngine, "stopEngine");
  }

  private static String parseCommand(String toCall) {
    if (toCall.startsWith("\"")) {
      int end;
      if ((end = toCall.indexOf("\"", 1)) > -1) {
        return toCall.substring(0, end + 1);
      } else {
        return toCall + "\"";
      }
    }
    int space;
    if ((space = toCall.indexOf(" ")) > -1) {
      return toCall.substring(0, space);
    }
    return toCall;
  }

  /**/

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#isLoaded()
   */
  public boolean isLoaded() {
    State state = getCurrentState();
    return state == State.STARTED || state == State.STARTING;
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#isStarted()
   */
  public boolean isStarted() {
    State state = getCurrentState();
    return state == State.STARTED || state == State.STOPPED;
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#installListeners()
   */
  public void installListeners() {
    if (preinitializeBegin.getAndSet(true)) return;
    LimeCoreGlue.preinstall();
    preinitializeDone.set(true);
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#loadBackgroundTasks()
   */
  public void loadBackgroundTasks() {
    if (backgroundBegin.getAndSet(true)) return;
    installListeners();
    // Don't try using GUIMediator.instance().uiPool()
    ThreadExecutor.startThread(this::doBackgroundTasks, "BackgroundTasks");
  }

  private void loadBackgroundTasksBlocking() {
    if (backgroundBegin.getAndSet(true)) return;
    installListeners();
    doBackgroundTasks();
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#start()
   */
  public void start() {
    synchronized (SHUTDOWN_ITEMS) {
      if (shutdownBegin.get() || startBegin.getAndSet(true)) {
        return;
      }
    }
    try {
      doStart();
    } finally {
      startLatch.countDown();
    }
  }

  private void doStart() {
    loadBackgroundTasksBlocking();
    if (ApplicationSettings.AUTOMATIC_MANUAL_GC.getValue()) startManualGCThread();
    startDone.set(true);
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#shutdown()
   */
  public void shutdown() {
    try {
      doShutdown();
    } catch (Throwable t) {
      ErrorService.error(t);
    }
  }

  private void doShutdown() {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("Shutdown must run off the EDT");
    }
    List<Thread> items;
    synchronized (SHUTDOWN_ITEMS) {
      if (shutdownBegin.getAndSet(true)) {
        return;
      }
      items = new ArrayList<>(SHUTDOWN_ITEMS);
      SHUTDOWN_ITEMS.clear();
    }
    // Start every owner before waiting, so one slow drain cannot delay another's revocation.
    AtomicBoolean failed = new AtomicBoolean();
    long deadline = System.nanoTime() + SHUTDOWN_ITEMS_TIMEOUT_NANOS;
    for (Thread item : items) {
      try {
        item.setUncaughtExceptionHandler(
            (thread, error) -> {
              failed.set(true);
              LOG.error("Shutdown item failed: " + thread.getName(), error);
            });
        item.start();
      } catch (Throwable t) {
        failed.set(true);
        LOG.error("Unable to start shutdown item: " + item.getName(), t);
      }
    }
    try {
      for (Thread item : items) {
        long remaining = deadline - System.nanoTime();
        if (remaining > 0) {
          TimeUnit.NANOSECONDS.timedJoin(item, remaining);
        }
        if (item.isAlive()) {
          LOG.warn("Shutdown still draining; native BTEngine retained");
          return;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Shutdown interrupted; native BTEngine retained", e);
      return;
    }
    if (failed.get() || !startBegin.get()) {
      return;
    }
    try {
      boolean started = startLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);
      if (!started) {
        LOG.warn("Timed out waiting for startup to finish, proceeding with shutdown anyway");
      }
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while waiting to finish starting", ie);
      return;
    }
    // save frostwire.props & other settings
    SettingsGroupManager.instance().save();
    LOG.info("Stopping BTEngine...");
    stopEngine.run();
    LOG.info("BTEngine stopped");
    shutdownDone.set(true);
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#shutdown(java.lang.String)
   */
  public void shutdown(String toExecute) {
    shutdown();
    if (shutdownDone.get() && toExecute != null) {
      try {
        String cmd = parseCommand(toExecute).trim();
        String params = toExecute.substring(cmd.length()).trim();
        new ProcessBuilder(cmd, params).start();
      } catch (IOException ignored) {
      }
    }
  }

  /* (non-Javadoc)
   * @see com.limegroup.gnutella.LifecycleManager#addShutdownItem(java.lang.Thread)
   */
  /**
   * Transfers an unstarted thread to shutdown. Normal termination must mean its borrowed resources
   * are drained, not merely that a wait timed out. All items start off the EDT before native stop,
   * with a shared five-second join budget. Failure or incomplete drain skips native disposal;
   * process exit may follow. Registration after shutdown begins throws so startup cannot silently
   * proceed.
   */
  public void addShutdownItem(Thread t) {
    Objects.requireNonNull(t, "shutdown item");
    synchronized (SHUTDOWN_ITEMS) {
      if (shutdownBegin.get()) {
        throw new IllegalStateException("Shutdown has already begun");
      }
      if (t.getState() != Thread.State.NEW) {
        throw new IllegalArgumentException("Shutdown item must not have been started");
      }
      if (!SHUTDOWN_ITEMS.contains(t)) {
        SHUTDOWN_ITEMS.add(t);
      }
    }
  }

  /** Runs all tasks that can be done in the background while the gui inits. */
  private void doBackgroundTasks() {
    limeCoreGlue.install(); // ensure glue is set before running tasks.
    backgroundDone.set(true);
  }

  /** Gets the current state of the lifecycle. */
  private State getCurrentState() {
    if (shutdownBegin.get()) return State.STOPPED;
    else if (startDone.get()) return State.STARTED;
    else if (startBegin.get()) return State.STARTING;
    else return State.NONE;
  }

  /** Starts a manual GC thread. */
  private void startManualGCThread() {
    Thread t =
        ThreadExecutor.newManagedThread(
            () -> {
              //noinspection InfiniteLoopStatement
              while (true) {
                try {
                  Thread.sleep(5 * 60 * 1000);
                } catch (InterruptedException ignored) {
                }
                // LOG.info("Running GC");
                System.gc();
              }
            },
            "ManualGC");
    t.setDaemon(true);
    t.start();
    // LOG.info("Started manual GC thread.");
  }

  private enum State {
    NONE,
    STARTING,
    STARTED,
    STOPPED
  }
}
