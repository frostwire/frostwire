package com.frostwire.util;

// StrictEdtMode.java

import com.limegroup.gnutella.gui.CrashReportSpooler;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Drop this utility in your app and call it before you create any Swing UI. It will:
 *
 * <p>watch the AWT Event Dispatch Thread (EDT),
 *
 * <p>if any event takes longer than your threshold (e.g., 300 ms), it will dump the EDT stack and
 * terminate the process (so you get a crash report pointing at the culprit).
 */
public final class StrictEdtMode {
  private static final ScheduledExecutorService SCH = newWatchdogScheduler();

  /**
   * One timer is armed per dispatched event and cancelled when the event finishes, so cancelled
   * timers must leave the queue at once; otherwise every event would leave a task behind for the
   * whole threshold (tens of thousands queued during event storms).
   */
  static ScheduledThreadPoolExecutor newWatchdogScheduler() {
    ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread thread = new Thread(runnable, "edt-strict-watchdog");
              thread.setDaemon(true);
              return thread;
            });
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  public static void install(Duration threshold) {
    EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    queue.push(new TimingEventQueue(threshold));
  }

  /**
   * Production variant: same breach detection and stderr dump as {@link #install(Duration)}, but
   * never halts. Instead it queues a throttled Icebase strictmode report and keeps dispatching
   * events.
   */
  public static void installProduction(Duration threshold) {
    EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    queue.push(new ProductionTimingEventQueue(threshold));
  }

  private static final class TimingEventQueue extends EventQueue {
    private final long thresholdMs;
    private final AtomicLong ticket = new AtomicLong();
    private volatile long currentTicket = -1;
    private volatile Thread edt;

    TimingEventQueue(Duration threshold) {
      this.thresholdMs = Math.max(1, threshold.toMillis());
    }

    @Override
    protected void dispatchEvent(AWTEvent event) {
      if (edt == null) edt = Thread.currentThread(); // capture EDT reference
      final long my = ticket.incrementAndGet();
      currentTicket = my;

      // arm a one-shot timer: if we're still handling THIS event after threshold -> dump & crash
      ScheduledFuture<?> stallTimer =
          SCH.schedule(
              () -> {
                if (currentTicket == my) {
                  dumpEdtAndCrash(event, edt, thresholdMs);
                }
              },
              thresholdMs,
              TimeUnit.MILLISECONDS);

      try {
        super.dispatchEvent(event);
      } finally {
        stallTimer.cancel(false);
        if (currentTicket == my) {
          currentTicket = -1;
        }
      }
    }

    private static void dumpEdtAndCrash(AWTEvent event, Thread edt, long thresholdMs) {
      System.err.printf(
          "=== STRICT-EDT VIOLATION: event took > %,d ms on %s ===%n", thresholdMs, edt);
      System.err.println("Event: " + event);

      StackTraceElement[] edtStack = edt.getStackTrace();
      System.err.println("--- EDT stack ---");
      for (StackTraceElement ste : edtStack) {
        System.err.println("\tat " + ste);
      }
      // Also include lock info (who might be blocking us)
      ThreadMXBean mx = ManagementFactory.getThreadMXBean();
      ThreadInfo ti = mx.getThreadInfo(edt.threadId());
      if (ti != null) {
        System.err.println("--- EDT lock info ---");
        System.err.println(ti.toString());
      }

      System.err.flush();
      if (isRenderPipelineStall(edtStack)) {
        System.err.println(
            "Strict EDT mode: render pipeline stall (pure JDK/Swing paint, no app frames); process will continue.");
        System.err.flush();
        return;
      }
      // Hard-fail the process so CI/test runs surface the problem immediately.
      Runtime.getRuntime().halt(42);
    }
  }

  private static final class ProductionTimingEventQueue extends EventQueue {
    private static final long REPORT_COOLDOWN_MS = 60_000;

    /** Grace period for the window-close event that runs the shutdown sequence. */
    static final long SHUTDOWN_THRESHOLD_MS = 10_000;

    private static volatile long lastProductionReportMillis;
    private final long thresholdMs;
    private final AtomicLong ticket = new AtomicLong();
    private volatile long currentTicket = -1;
    private volatile Thread edt;

    ProductionTimingEventQueue(Duration threshold) {
      this.thresholdMs = Math.max(1, threshold.toMillis());
    }

    @Override
    protected void dispatchEvent(AWTEvent event) {
      if (edt == null) edt = Thread.currentThread(); // capture EDT reference
      final long my = ticket.incrementAndGet();
      currentTicket = my;

      // arm a one-shot timer: if we're still handling THIS event after threshold -> dump & report
      ScheduledFuture<?> stallTimer =
          SCH.schedule(
              () -> {
                if (currentTicket != my) {
                  return;
                }
                // Only the event running Finalizer.shutdown gets extra time; everything else,
                // including later events while the app exits, keeps the normal threshold.
                if (isShutdownSequence(edt.getStackTrace())
                    && SHUTDOWN_THRESHOLD_MS > thresholdMs) {
                  SCH.schedule(
                      () -> {
                        if (currentTicket == my) {
                          dumpEdtAndQueueReport(
                              event,
                              edt,
                              SHUTDOWN_THRESHOLD_MS,
                              SHUTDOWN_TAG,
                              () -> currentTicket == my);
                        }
                      },
                      SHUTDOWN_THRESHOLD_MS - thresholdMs,
                      TimeUnit.MILLISECONDS);
                } else {
                  dumpEdtAndQueueReport(event, edt, thresholdMs, "", () -> currentTicket == my);
                }
              },
              thresholdMs,
              TimeUnit.MILLISECONDS);

      try {
        super.dispatchEvent(event);
      } finally {
        // A stall already being reported (or the shutdown re-arm) has started; cancel(false)
        // never interrupts it, and only drops timers for events that finished in time.
        stallTimer.cancel(false);
        if (currentTicket == my) {
          currentTicket = -1;
        }
      }
    }

    private static void dumpEdtAndQueueReport(
        AWTEvent event,
        Thread edt,
        long thresholdMs,
        String tag,
        BooleanSupplier stalledEventStillRunning) {
      System.err.printf(
          "=== STRICT-EDT VIOLATION: event took > %,d ms on %s ===%n", thresholdMs, edt);
      System.err.println("Event: " + event);

      // Sample only the EDT: walking every thread is slow on small machines and widens the
      // window in which the stalled event finishes before its stack is captured.
      StackTraceElement[] edtStack = edt.getStackTrace();
      // The stalled event may finish while the stack is taken; the sample then shows other work.
      boolean sampledTheStalledEvent = stalledEventStillRunning.getAsBoolean();
      System.err.println("--- EDT stack ---");
      for (StackTraceElement ste : edtStack) {
        System.err.println("\tat " + ste);
      }
      // Also include lock info (who might be blocking us)
      ThreadMXBean mx = ManagementFactory.getThreadMXBean();
      ThreadInfo ti = mx.getThreadInfo(edt.threadId());
      if (ti != null) {
        System.err.println("--- EDT lock info ---");
        System.err.println(ti.toString());
      }

      System.err.flush();
      if (isRenderPipelineStall(edtStack)) {
        System.err.println("Strict EDT mode: render pipeline stall; skipping Icebase report.");
        System.err.flush();
        return;
      }
      if (isWindowsToolkitOnlyStall(edtStack)) {
        System.err.println("Strict EDT mode: Windows toolkit-only stall; skipping Icebase report.");
        System.err.flush();
        return;
      }
      // Production never halts: queue a throttled Icebase report and keep dispatching.
      // Bounded descriptor on purpose: the raw event string can carry coordinates and
      // other high-cardinality data that would explode server-side issue grouping.
      tag = reportTag(tag, edtStack, sampledTheStalledEvent);
      maybeQueueProductionReport(tag + event.getClass().getName() + "#" + event.getID(), edtStack);
    }

    private static void maybeQueueProductionReport(
        String eventDescription, StackTraceElement[] edtStack) {
      long now = System.currentTimeMillis();
      if (now - lastProductionReportMillis < REPORT_COOLDOWN_MS) {
        return;
      }
      lastProductionReportMillis = now;
      try {
        CrashReportSpooler.recordStrictMode(eventDescription, 1, edtStack);
      } catch (Throwable ignored) {
        // Reporting must never break event dispatch.
      }
    }
  }

  /**
   * The title prefix that groups a production stall report: the caller's tag when it has one,
   * otherwise one derived from the sampled stack. {@code sampledTheStalledEvent} is false when the
   * stalled event had already finished (or a nested event was running) once the stack was taken, so
   * the stack belongs to other work.
   *
   * <p>Thread-safe: stateless.
   */
  static String reportTag(String tag, StackTraceElement[] stack, boolean sampledTheStalledEvent) {
    if (!tag.isEmpty()) {
      return tag;
    }
    if (!sampledTheStalledEvent || !isInsideWatchedDispatch(stack)) {
      return STACK_MISSED_TAG;
    }
    if (isRenderQueueLockWait(stack)) {
      return RENDER_QUEUE_TAG;
    }
    if (isJdkShellFolderStall(stack)) {
      return SHELL_FOLDER_TAG;
    }
    return "";
  }

  static final String SHUTDOWN_TAG = "shutdown:";
  static final String SHELL_FOLDER_TAG = "jdk-shellfolder:";
  static final String STACK_MISSED_TAG = "stack-missed:";
  static final String RENDER_QUEUE_TAG = "render-queue:";

  /**
   * True when the EDT is blocked acquiring {@code SunToolkit.awtLock} inside Java2D's {@code
   * RenderQueue.lock}: the render-queue flusher holds that lock while it waits on the GPU driver
   * (OpenGL on Windows, Metal on macOS), so any drawing call on the EDT waits. Only the frames
   * above the lock wait count; app paint code below it (the caller) is expected, but an app frame
   * above it is not. Only the JDK takes awtLock, so app locks never match.
   *
   * <p>Thread-safe: stateless stack inspection.
   */
  static boolean isRenderQueueLockWait(StackTraceElement[] stack) {
    if (stack == null) {
      return false;
    }
    boolean waitingOnAwtLock = false;
    for (StackTraceElement frame : stack) {
      if (frame == null || frame.getClassName() == null) {
        continue;
      }
      String className = frame.getClassName();
      if (className.startsWith(WATCHDOG_PREFIX)) {
        return false;
      }
      for (String appPrefix : APP_FRAME_PREFIXES) {
        if (className.startsWith(appPrefix)) {
          return false;
        }
      }
      if ("sun.awt.SunToolkit".equals(className) && "awtLock".equals(frame.getMethodName())) {
        waitingOnAwtLock = true;
      } else if (waitingOnAwtLock
          && "sun.java2d.pipe.RenderQueue".equals(className)
          && "lock".equals(frame.getMethodName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * True when the EDT stack still shows the watchdog dispatching an event. False means the stalled
   * event finished between the timer firing and the stack being sampled (the EDT is idle or on a
   * new event's pump), so the stack says nothing about what stalled.
   *
   * <p>Read from the top: an EDT waiting in {@code EventQueue.getNextEvent} is idle even when a
   * watched dispatch sits further down, as when the stalled event opened a modal dialog and the
   * dialog's nested event loop is now waiting. The frames below still name the event that ran long,
   * but not the work that made it slow.
   *
   * <p>Thread-safe: stateless stack inspection.
   */
  static boolean isInsideWatchedDispatch(StackTraceElement[] stack) {
    if (stack == null) {
      return false;
    }
    for (StackTraceElement frame : stack) {
      if (frame == null || frame.getClassName() == null) {
        continue;
      }
      if ("java.awt.EventQueue".equals(frame.getClassName())
          && "getNextEvent".equals(frame.getMethodName())) {
        return false;
      }
      if (frame.getClassName().startsWith(WATCHDOG_PREFIX)
          && "dispatchEvent".equals(frame.getMethodName())) {
        return true;
      }
    }
    return false;
  }

  private static final String SHELL_FOLDER_PREFIX = "sun.awt.shell.";

  /**
   * True when the stalled event is stuck in the JDK's shell-folder layer (e.g. JFileChooser listing
   * or checking access to a slow, network or cloud-placeholder folder through {@code
   * sun.awt.shell.*}, including the Windows COM thread) with no app code involved. Only the frames
   * above the innermost watchdog dispatch are considered: those belong to the stalled event, while
   * app frames further down (the code that opened a modal dialog) do not disqualify it. Any app
   * frame in the stalled event's own frames does.
   *
   * <p>Thread-safe: stateless stack inspection.
   */
  static boolean isJdkShellFolderStall(StackTraceElement[] stack) {
    if (stack == null) {
      return false;
    }
    boolean hasShellFolder = false;
    for (StackTraceElement frame : stack) {
      if (frame == null || frame.getClassName() == null) {
        continue;
      }
      String className = frame.getClassName();
      if (className.startsWith(WATCHDOG_PREFIX)) {
        return hasShellFolder;
      }
      for (String appPrefix : APP_FRAME_PREFIXES) {
        if (className.startsWith(appPrefix)) {
          return false;
        }
      }
      if (className.startsWith(SHELL_FOLDER_PREFIX)) {
        hasShellFolder = true;
      }
    }
    return false;
  }

  private static final String FINALIZER_CLASS = "com.limegroup.gnutella.gui.Finalizer";

  /**
   * True when the EDT is inside {@code Finalizer.shutdown}, i.e. handling the event that starts the
   * app's exit sequence. Stateless stack inspection, safe from any thread.
   */
  static boolean isShutdownSequence(StackTraceElement[] stack) {
    if (stack == null) {
      return false;
    }
    for (StackTraceElement frame : stack) {
      if (frame != null
          && FINALIZER_CLASS.equals(frame.getClassName())
          && "shutdown".equals(frame.getMethodName())) {
        return true;
      }
    }
    return false;
  }

  private static final String JAVA2D_PREFIX = "sun.java2d.";
  private static final String SWING_PREFIX = "javax.swing.";
  private static final String FLATLAF_PREFIX = "com.formdev.";
  private static final String WATCHDOG_PREFIX = "com.frostwire.util.StrictEdtMode";
  private static final String[] APP_FRAME_PREFIXES = {
    "com.frostwire.", "com.limegroup.", "org.limewire."
  };

  /**
   * Stalled events spent entirely inside a Windows toolkit call FrostWire neither makes nor can
   * change, sampled while the machine or the toolkit thread was busy. Each entry is the exact
   * stalled segment as "class.method", top of stack first. Icebase drops the same signatures at
   * ingest (frostwire-cloud {@code icebase.frostwire.com/src/report_filter.php}); keep both lists
   * in sync.
   */
  static final String[][] WINDOWS_TOOLKIT_ONLY_STALLS = {
    // The cursor manager's own periodic update, posted by the toolkit.
    {
      "sun.awt.windows.WGlobalCursorManager.findHeavyweightUnderCursor",
      "sun.awt.GlobalCursorManager._updateCursor",
      "sun.awt.GlobalCursorManager$NativeUpdater.run"
    },
    // Validation start of a native window resize event.
    {
      "sun.awt.windows.WComponentPeer.beginValidate",
      "java.awt.Container.validate",
      "java.awt.Window.dispatchEventImpl",
      "java.awt.Component.dispatchEvent"
    }
  };

  /** Frames where the EDT's event loop dispatches; frames above the first one are the stall. */
  private static final String[] DISPATCH_FRAME_PREFIXES = {
    "java.awt.EventQueue",
    "java.awt.EventDispatchThread",
    "java.awt.event.InvocationEvent",
    WATCHDOG_PREFIX
  };

  /**
   * True when the frames above the event dispatch are exactly one of {@link
   * #WINDOWS_TOOLKIT_ONLY_STALLS}. The same native call reached from FrostWire code or from a Swing
   * layout pass (where an app revalidate storm can be the cause, GitHub #1321) does not match, so
   * it is still reported.
   *
   * <p>Thread-safe: stateless stack inspection.
   */
  static boolean isWindowsToolkitOnlyStall(StackTraceElement[] stack) {
    if (stack == null) {
      return false;
    }
    java.util.List<String> segment = new java.util.ArrayList<>();
    for (StackTraceElement frame : stack) {
      if (frame == null || frame.getClassName() == null) {
        return false;
      }
      String className = frame.getClassName();
      boolean dispatch = false;
      for (String prefix : DISPATCH_FRAME_PREFIXES) {
        if (className.startsWith(prefix)) {
          dispatch = true;
          break;
        }
      }
      if (dispatch) {
        break;
      }
      segment.add(className + "." + frame.getMethodName());
    }
    for (String[] signature : WINDOWS_TOOLKIT_ONLY_STALLS) {
      if (java.util.Arrays.asList(signature).equals(segment)) {
        return true;
      }
    }
    return false;
  }

  static boolean isMacMetalRenderPipelineStall(StackTraceElement[] stack) {
    if (stack == null) {
      return false;
    }
    for (StackTraceElement frame : stack) {
      if (frame != null
          && "sun.java2d.metal.MTLRenderQueue$QueueFlusher".equals(frame.getClassName())
          && "flushNow".equals(frame.getMethodName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Pure JDK/Swing paint stall (render pipeline, not an app defect).
   *
   * <p>Thread-safe: stateless stack inspection, no shared mutable state.
   *
   * <p>True when the EDT is busy inside the Java2D software/hardware rasterizer ({@code
   * sun.java2d.*}) reached from Swing/AWT paint, with no FrostWire app frames on the stack. This
   * covers pure JDK paint stalls (MaskBlit/Blit via SpanShapeRenderer, image blits, glyph runs)
   * that can exceed the Strict-EDT threshold while RUNNABLE in native code. Any {@code
   * com.frostwire.*}, {@code com.limegroup.*} or {@code org.limewire.*} frame disqualifies the
   * exemption so real app work on the EDT still fails fast. The watchdog's own {@code
   * StrictEdtMode$TimingEventQueue.dispatchEvent} frame is always on the EDT stack and is ignored.
   */
  static boolean isRenderPipelineStall(StackTraceElement[] stack) {
    if (stack == null || stack.length == 0) {
      return false;
    }
    if (isMacMetalRenderPipelineStall(stack)) {
      return true;
    }
    boolean hasJava2dRaster = false;
    boolean hasSwingPaint = false;
    for (StackTraceElement frame : stack) {
      if (frame == null) {
        continue;
      }
      String className = frame.getClassName();
      if (className == null) {
        continue;
      }
      if (className.startsWith(WATCHDOG_PREFIX)) {
        continue;
      }
      for (String appPrefix : APP_FRAME_PREFIXES) {
        if (className.startsWith(appPrefix)) {
          return false;
        }
      }
      if (className.startsWith(JAVA2D_PREFIX)) {
        hasJava2dRaster = true;
      }
      if (className.startsWith(SWING_PREFIX)
          || className.startsWith(FLATLAF_PREFIX)
          || "sun.awt.SunGraphicsCallback".equals(className)
          || "java.awt.GraphicsCallback".equals(className)) {
        hasSwingPaint = true;
      } else if (className.startsWith("java.awt.")
          && frame.getMethodName() != null
          && frame.getMethodName().contains("paint")) {
        hasSwingPaint = true;
      }
    }
    return hasJava2dRaster && hasSwingPaint;
  }
}
