package com.frostwire.util;

// StrictEdtMode.java

import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
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
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edt-strict-watchdog");
                t.setDaemon(true);
                return t;
            });

    public static void install(Duration threshold) {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        queue.push(new TimingEventQueue(threshold));
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

            super.dispatchEvent(event);
        }

        private static void dumpEdtAndCrash(AWTEvent event, Thread edt, long thresholdMs) {
            System.err.printf("=== STRICT-EDT VIOLATION: event took > %,d ms on %s ===%n",
                    thresholdMs, edt);
            System.err.println("Event: " + event);

            // Grab stack of all threads and print EDT’s
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                if (e.getKey() == edt) {
                    System.err.println("--- EDT stack ---");
                    for (StackTraceElement ste : e.getValue()) {
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
            // Hard-fail the process so CI/test runs surface the problem immediately.
            Runtime.getRuntime().halt(42);
        }
    }
}
