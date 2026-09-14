package com.frostwire.util;

// StrictEdtMode.java

import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.limegroup.gnutella.gui.CrashReportSpooler;

import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drop this utility in your app and call it before you create any Swing UI. It will:
 * <p>
 * watch the AWT Event Dispatch Thread (EDT),
 * <p>
 * if any event takes longer than your threshold (e.g., 300 ms), it will dump the EDT stack and terminate the process (so you get a crash report pointing at the culprit).
 */
public final class StrictEdtMode {
    private static final ScheduledExecutorService SCH =
            ExecutorsHelper.newScheduledThreadPool(1, "edt-strict-watchdog");

    public static void install(Duration threshold) {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        queue.push(new TimingEventQueue(threshold));
    }

    /**
     * Production variant: same breach detection and stderr dump as {@link #install(Duration)},
     * but never halts. Instead it queues a throttled Icebase watchdog/strictmode report and
     * keeps dispatching events.
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
            SCH.schedule(() -> {
                if (currentTicket == my) {
                    dumpEdtAndCrash(event, edt, thresholdMs);
                }
            }, thresholdMs, TimeUnit.MILLISECONDS);

            try {
                super.dispatchEvent(event);
            } finally {
                if (currentTicket == my) {
                    currentTicket = -1;
                }
            }
        }

        private static void dumpEdtAndCrash(AWTEvent event, Thread edt, long thresholdMs) {
            System.err.printf("=== STRICT-EDT VIOLATION: event took > %,d ms on %s ===%n",
                    thresholdMs, edt);
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
                System.err.println("Strict EDT mode: render pipeline stall (pure JDK/Swing paint, no app frames); process will continue.");
                System.err.flush();
                return;
            }
            // Hard-fail the process so CI/test runs surface the problem immediately.
            Runtime.getRuntime().halt(42);
        }
    }

    private static final class ProductionTimingEventQueue extends EventQueue {
        private static final long REPORT_COOLDOWN_MS = 60_000;
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
            SCH.schedule(() -> {
                if (currentTicket == my) {
                    dumpEdtAndQueueReport(event, edt, thresholdMs);
                }
            }, thresholdMs, TimeUnit.MILLISECONDS);

            try {
                super.dispatchEvent(event);
            } finally {
                if (currentTicket == my) {
                    currentTicket = -1;
                }
            }
        }

        private static void dumpEdtAndQueueReport(AWTEvent event, Thread edt, long thresholdMs) {
            System.err.printf("=== STRICT-EDT VIOLATION: event took > %,d ms on %s ===%n",
                    thresholdMs, edt);
            System.err.println("Event: " + event);

            // Grab stack of all threads and print EDT’s
            StackTraceElement[] edtStack = null;
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                if (e.getKey() == edt) {
                    edtStack = e.getValue();
                    System.err.println("--- EDT stack ---");
                    for (StackTraceElement ste : edtStack) {
                        System.err.println("\tat " + ste);
                    }
                }
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
            // Production never halts: queue a throttled Icebase report and keep dispatching.
            // Bounded descriptor on purpose: the raw event string can carry coordinates and
            // other high-cardinality data that would explode server-side issue grouping.
            maybeQueueProductionReport(event.getClass().getName() + "#" + event.getID());
        }

        private static void maybeQueueProductionReport(String eventDescription) {
            long now = System.currentTimeMillis();
            if (now - lastProductionReportMillis < REPORT_COOLDOWN_MS) {
                return;
            }
            lastProductionReportMillis = now;
            try {
                CrashReportSpooler.recordStrictMode(eventDescription, 1);
                CrashReportSpooler.recordWatchdog(1);
            } catch (Throwable ignored) {
                // Reporting must never break event dispatch.
            }
        }
    }

    private static final String JAVA2D_PREFIX = "sun.java2d.";
    private static final String SWING_PREFIX = "javax.swing.";
    private static final String FLATLAF_PREFIX = "com.formdev.";
    private static final String WATCHDOG_PREFIX = "com.frostwire.util.StrictEdtMode";
    private static final String[] APP_FRAME_PREFIXES = {
            "com.frostwire.",
            "com.limegroup.",
            "org.limewire."
    };

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
     * <p>True when the EDT is busy inside the Java2D software/hardware rasterizer
     * ({@code sun.java2d.*}) reached from Swing/AWT paint, with no FrostWire app
     * frames on the stack. This covers the Linux XWayland path where
     * {@code -Dsun.java2d.xrender=false -Dsun.java2d.opengl=false} forces
     * software loops (MaskBlit/Blit via SpanShapeRenderer, e.g. EtchedBorder
     * border painting) that can exceed the Strict-EDT threshold on first paint
     * while RUNNABLE in native code. Any {@code com.frostwire.*},
     * {@code com.limegroup.*} or {@code org.limewire.*} frame disqualifies the
     * exemption so real app work on the EDT still fails fast. The watchdog's own
     * {@code StrictEdtMode$TimingEventQueue.dispatchEvent} frame is always on the
     * EDT stack and is ignored.
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
