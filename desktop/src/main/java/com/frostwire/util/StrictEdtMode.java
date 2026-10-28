package com.frostwire.util;

// StrictEdtMode.java

import com.limegroup.gnutella.gui.CrashReportSpooler;
import java.awt.*;
import java.lang.management.GarbageCollectorMXBean;
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
  private static final TimeUnit MS = TimeUnit.MILLISECONDS;

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

      // One timer per event, re-armed in steps: an early sample halfway, then the threshold check.
      StallWatch watch = new StallWatch(event, my);
      watch.timer = SCH.schedule(watch::takeEarlySample, earlySampleDelayMs(thresholdMs), MS);

      try {
        super.dispatchEvent(event);
      } finally {
        // A stall already being reported (or the shutdown re-arm) has started; cancel(false)
        // never interrupts it, and only drops timers for events that finished in time.
        watch.timer.cancel(false);
        if (currentTicket == my) {
          currentTicket = -1;
        }
      }
    }

    /**
     * The timer steps of one dispatched event. Steps run one after another on the single watchdog
     * thread; {@link #timer} is volatile because the EDT reads it to cancel the pending step.
     */
    private final class StallWatch {
      private final AWTEvent event;
      private final long my;
      private volatile ScheduledFuture<?> timer;

      /** The EDT stack halfway to the threshold, while this event was still running; or null. */
      private StackTraceElement[] earlySample;

      /** GC totals and the time when the early sample was due; null until then. */
      private GcTotals gcAtEarlySample;

      private long earlySampleNanos;

      StallWatch(AWTEvent event, long my) {
        this.event = event;
        this.my = my;
      }

      private boolean stillRunning() {
        return currentTicket == my;
      }

      /**
       * Samples the EDT halfway to the threshold, so a stall that ends just before the threshold
       * sample still has a stack showing what it was doing.
       */
      void takeEarlySample() {
        if (!stillRunning()) {
          return;
        }
        // Only events already running this long pay for reading the GC counters.
        gcAtEarlySample = GcTotals.now();
        earlySampleNanos = System.nanoTime();
        StackTraceElement[] stack = edt.getStackTrace();
        if (stillRunning() && isInsideWatchedDispatch(stack)) {
          earlySample = stack;
        }
        timer =
            SCH.schedule(this::checkThreshold, thresholdMs - earlySampleDelayMs(thresholdMs), MS);
      }

      void checkThreshold() {
        if (!stillRunning()) {
          return;
        }
        // Only the event running Finalizer.shutdown gets extra time; everything else,
        // including later events while the app exits, keeps the normal threshold.
        if (isShutdownSequence(edt.getStackTrace()) && SHUTDOWN_THRESHOLD_MS > thresholdMs) {
          timer =
              SCH.schedule(
                  () -> {
                    if (stillRunning()) {
                      dumpEdtAndQueueReport(
                          event,
                          edt,
                          SHUTDOWN_THRESHOLD_MS,
                          SHUTDOWN_TAG,
                          this::stillRunning,
                          null,
                          null);
                    }
                  },
                  SHUTDOWN_THRESHOLD_MS - thresholdMs,
                  MS);
        } else {
          dumpEdtAndQueueReport(
              event,
              edt,
              thresholdMs,
              "",
              this::stillRunning,
              earlySample,
              gcWindow(gcAtEarlySample, earlySampleNanos, GcTotals.now(), System.nanoTime()));
        }
      }
    }

    private static void dumpEdtAndQueueReport(
        AWTEvent event,
        Thread edt,
        long thresholdMs,
        String tag,
        BooleanSupplier stalledEventStillRunning,
        StackTraceElement[] earlySample,
        CrashReportSpooler.GcWindow gcWindow) {
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
      StallReport report = stallReport(tag, edtStack, sampledTheStalledEvent, earlySample);
      // Checked on the stack to be reported, which may be the early sample.
      if (isWindowsToolkitOnlyStall(report.stack())) {
        System.err.println("Strict EDT mode: Windows toolkit-only stall; skipping Icebase report.");
        System.err.flush();
        return;
      }
      if (report.stack() != edtStack) {
        System.err.println("--- EDT stack halfway to the threshold (reported instead) ---");
        for (StackTraceElement ste : report.stack()) {
          System.err.println("\tat " + ste);
        }
        System.err.flush();
      }
      // Production never halts: queue a throttled Icebase report and keep dispatching.
      // Bounded descriptor on purpose: the raw event string can carry coordinates and
      // other high-cardinality data that would explode server-side issue grouping.
      if (gcWindow != null) {
        System.err.printf(
            "--- GC: %d collections, %,d ms in the last %,d ms ---%n",
            gcWindow.count(), gcWindow.timeMs(), gcWindow.windowMs());
        System.err.flush();
      }
      maybeQueueProductionReport(
          report.tag() + event.getClass().getName() + "#" + event.getID(),
          report.stack(),
          gcWindow);
    }

    private static void maybeQueueProductionReport(
        String eventDescription,
        StackTraceElement[] edtStack,
        CrashReportSpooler.GcWindow gcWindow) {
      long now = System.currentTimeMillis();
      if (now - lastProductionReportMillis < REPORT_COOLDOWN_MS) {
        return;
      }
      lastProductionReportMillis = now;
      try {
        CrashReportSpooler.recordStrictMode(eventDescription, 1, edtStack, gcWindow);
      } catch (Throwable ignored) {
        // Reporting must never break event dispatch.
      }
    }
  }

  /** Collections and time spent in them since the JVM started, over every collector. */
  record GcTotals(long count, long timeMs) {
    static GcTotals now() {
      long count = 0;
      long timeMs = 0;
      for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
        // -1 when a collector doesn't track it.
        count += Math.max(0, collector.getCollectionCount());
        timeMs += Math.max(0, collector.getCollectionTime());
      }
      return new GcTotals(count, timeMs);
    }
  }

  /**
   * The GC activity between two readings, or null without a first one. GC time is counted in whole
   * milliseconds per collection, so it is capped at the window it was measured in.
   */
  static CrashReportSpooler.GcWindow gcWindow(
      GcTotals start, long startNanos, GcTotals end, long endNanos) {
    if (start == null || end == null) {
      return null;
    }
    int windowMs = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos));
    int count = (int) Math.max(0, end.count() - start.count());
    int timeMs = (int) Math.min(windowMs, Math.max(0, end.timeMs() - start.timeMs()));
    return new CrashReportSpooler.GcWindow(count, timeMs, windowMs);
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

  /** A production stall report's title prefix and the EDT stack it carries. */
  record StallReport(String tag, StackTraceElement[] stack) {}

  /**
   * Picks the stack and title prefix of a production stall report. The threshold sample is used
   * unless it missed the stalled event (see {@link #reportTag}); then an {@code earlySample} taken
   * halfway to the threshold, while the same event was still running, is reported instead under
   * {@link #EARLY_SAMPLE_TAG} (followed by any tag derived from that stack). Without an early
   * sample the report stays {@link #STACK_MISSED_TAG} with the threshold sample, as before.
   *
   * <p>Thread-safe: stateless.
   */
  static StallReport stallReport(
      String tag,
      StackTraceElement[] atThreshold,
      boolean sampledTheStalledEvent,
      StackTraceElement[] earlySample) {
    String thresholdTag = reportTag(tag, atThreshold, sampledTheStalledEvent);
    if (STACK_MISSED_TAG.equals(thresholdTag) && isInsideWatchedDispatch(earlySample)) {
      return new StallReport(EARLY_SAMPLE_TAG + reportTag("", earlySample, true), earlySample);
    }
    return new StallReport(thresholdTag, atThreshold);
  }

  /**
   * When the early sample is taken: halfway to the threshold.
   *
   * <p>Thread-safe: stateless.
   */
  static long earlySampleDelayMs(long thresholdMs) {
    return thresholdMs / 2;
  }

  static final String SHUTDOWN_TAG = "shutdown:";
  static final String SHELL_FOLDER_TAG = "jdk-shellfolder:";
  static final String STACK_MISSED_TAG = "stack-missed:";
  static final String EARLY_SAMPLE_TAG = "early-sample:";
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
   * First element of a {@link #WINDOWS_TOOLKIT_ONLY_STALLS} signature whose top frames vary: any
   * number of JDK frames ({@link #JDK_FRAME_PREFIXES}) may sit above the rest of the signature.
   */
  static final String ANY_JDK_FRAMES = "*";

  private static final String[] JDK_FRAME_PREFIXES = {"java.", "javax.", "sun.", "jdk."};

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
    },
    // The Windows input method (IME) deactivating on a focus change, before any focus listener
    // runs; the sample lands anywhere in the JDK code the native call goes back into.
    {
      ANY_JDK_FRAMES,
      "sun.awt.windows.WInputMethod.getNativeLocale",
      "sun.awt.windows.WInputMethod.getLocale",
      "sun.awt.windows.WInputMethod.deactivate",
      "sun.awt.im.InputContext.deactivateInputMethod",
      "sun.awt.im.InputContext.focusLost",
      "sun.awt.im.InputContext.dispatchEvent",
      "sun.awt.im.InputMethodContext.dispatchEvent",
      "java.awt.Component.dispatchEventImpl",
      "java.awt.Container.dispatchEventImpl",
      "java.awt.Component.dispatchEvent",
      "java.awt.KeyboardFocusManager.redispatchEvent",
      "java.awt.DefaultKeyboardFocusManager.typeAheadAssertions",
      "java.awt.DefaultKeyboardFocusManager.dispatchEvent",
      "java.awt.Component.dispatchEventImpl",
      "java.awt.Container.dispatchEventImpl",
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
      if (matchesStallSignature(signature, segment)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the stalled segment is the signature: exactly, or for a signature starting with {@link
   * #ANY_JDK_FRAMES}, the rest of the signature below only JDK frames.
   */
  private static boolean matchesStallSignature(String[] signature, java.util.List<String> segment) {
    java.util.List<String> frames = java.util.Arrays.asList(signature);
    if (frames.isEmpty() || !ANY_JDK_FRAMES.equals(frames.get(0))) {
      return frames.equals(segment);
    }
    java.util.List<String> tail = frames.subList(1, frames.size());
    int above = segment.size() - tail.size();
    if (above < 0 || !segment.subList(above, segment.size()).equals(tail)) {
      return false;
    }
    for (String frame : segment.subList(0, above)) {
      boolean jdk = false;
      for (String prefix : JDK_FRAME_PREFIXES) {
        jdk |= frame.startsWith(prefix);
      }
      if (!jdk) {
        return false;
      }
    }
    return true;
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
