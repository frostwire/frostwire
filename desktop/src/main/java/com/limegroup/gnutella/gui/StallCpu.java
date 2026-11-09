/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Where the CPU went while the EDT was stalled, measured over the same window as the stall's GC
 * numbers (frostwire/frostwire#1512). A sampled EDT stack that stops in a cheap frame says nothing
 * about the stall; these numbers tell which of four cases it was:
 *
 * <ul>
 *   <li>EDT CPU close to the window: the EDT was busy, the work is on the EDT.
 *   <li>EDT CPU low, process CPU high: the app's own threads starved the EDT; {@code cpu_threads}
 *       names them.
 *   <li>Both low, system load high: other processes took the machine.
 *   <li>Everything low: the process wasn't running at all (paged out, suspended); free physical
 *       memory tells paging apart.
 * </ul>
 *
 * Thread names are grouped like {@link ThreadCensus#groupName(String)}, so they carry no search
 * text or URLs. Needs the {@code jdk.management} module; each number it can't read is left out.
 */
public final class StallCpu {
  static final int MAX_THREAD_GROUPS = 5;

  /** CPU counters at one moment. */
  public static final class Snapshot {
    final long edtCpuNs;
    final long processCpuNs;
    final Map<Long, Long> threadCpuNs;

    Snapshot(long edtCpuNs, long processCpuNs, Map<Long, Long> threadCpuNs) {
      this.edtCpuNs = edtCpuNs;
      this.processCpuNs = processCpuNs;
      this.threadCpuNs = threadCpuNs;
    }
  }

  /** A thread group and the CPU time its threads used in the window. Fields are JSON keys. */
  public static final class ThreadCpu {
    private final String name;
    private final long cpu_ms;

    ThreadCpu(String name, long cpuMs) {
      this.name = name;
      this.cpu_ms = cpuMs;
    }

    public String name() {
      return name;
    }

    public long cpuMs() {
      return cpu_ms;
    }
  }

  /**
   * CPU use in a window. Each number is null, and left out of the report, when it couldn't be read.
   */
  public record Window(
      Integer edtCpuMs,
      Integer processCpuMs,
      Integer systemCpuPct,
      Integer physFreeMb,
      List<ThreadCpu> busiestThreads) {}

  private StallCpu() {}

  /**
   * The counters now, or null when they can't be read. Also asks for the system CPU load once, so
   * that the reading at the end of the window has a previous sample to compare with.
   */
  public static Snapshot take(Thread edt) {
    return take(edt, true);
  }

  private static Snapshot take(Thread edt, boolean primeSystemLoad) {
    try {
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      Map<Long, Long> perThread = new HashMap<>();
      long[] ids = threads.getAllThreadIds();
      long[] cpu = HotSpot.AVAILABLE ? HotSpot.threadCpuTimes(threads, ids) : null;
      if (cpu != null) {
        for (int i = 0; i < ids.length; i++) {
          if (cpu[i] >= 0) {
            perThread.put(ids[i], cpu[i]);
          }
        }
      }
      long edtCpu = threadCpuTimeSupported(threads) ? threads.getThreadCpuTime(edt.threadId()) : -1;
      long processCpu = HotSpot.AVAILABLE ? HotSpot.processCpuTime() : -1;
      if (primeSystemLoad && HotSpot.AVAILABLE) {
        // The load is measured since the previous call, so this one starts the window.
        HotSpot.systemCpuLoad();
      }
      return new Snapshot(edtCpu, processCpu, perThread);
    } catch (RuntimeException | LinkageError e) {
      return null;
    }
  }

  /** CPU use between {@code start} and now, or null without a start. */
  public static Window since(Snapshot start, Thread edt) {
    if (start == null) {
      return null;
    }
    try {
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      Snapshot end = take(edt, false);
      if (end == null) {
        return null;
      }
      long[] ids = new long[end.threadCpuNs.size()];
      int n = 0;
      for (Long id : end.threadCpuNs.keySet()) {
        ids[n++] = id;
      }
      ThreadInfo[] infos = threads.getThreadInfo(ids, 0);
      String[] names = new String[ids.length];
      long[] cpuNs = new long[ids.length];
      for (int i = 0; i < ids.length; i++) {
        names[i] = infos[i] == null ? null : infos[i].getThreadName();
        // A thread started inside the window used all its CPU time in it.
        cpuNs[i] = end.threadCpuNs.get(ids[i]) - start.threadCpuNs.getOrDefault(ids[i], 0L);
      }
      Integer systemPct = null;
      Integer freeMb = null;
      if (HotSpot.AVAILABLE) {
        double load = HotSpot.systemCpuLoad();
        if (load >= 0) {
          systemPct = (int) Math.round(Math.min(1, load) * 100);
        }
        long free = HotSpot.freeMemoryBytes();
        if (free >= 0) {
          freeMb = (int) Math.min(Integer.MAX_VALUE, free / (1024 * 1024));
        }
      }
      return new Window(
          deltaMs(start.edtCpuNs, end.edtCpuNs),
          deltaMs(start.processCpuNs, end.processCpuNs),
          systemPct,
          freeMb,
          busiest(names, cpuNs));
    } catch (RuntimeException | LinkageError e) {
      return null;
    }
  }

  /**
   * The {@link #MAX_THREAD_GROUPS} thread groups that used the most CPU, most first, leaving out
   * groups that used none. {@code cpuNs} is parallel to {@code names}; a null name is a thread that
   * ended and isn't counted.
   */
  static List<ThreadCpu> busiest(String[] names, long[] cpuNs) {
    Map<String, Long> totals = new HashMap<>();
    for (int i = 0; i < names.length; i++) {
      if (names[i] != null && cpuNs[i] > 0) {
        totals.merge(ThreadCensus.groupName(names[i]), cpuNs[i], Long::sum);
      }
    }
    List<ThreadCpu> groups = new ArrayList<>();
    totals.forEach(
        (name, ns) -> {
          long ms = TimeUnit.NANOSECONDS.toMillis(ns);
          if (ms > 0) {
            groups.add(new ThreadCpu(name, ms));
          }
        });
    groups.sort(
        Comparator.comparingLong(ThreadCpu::cpuMs).reversed().thenComparing(ThreadCpu::name));
    return groups.size() > MAX_THREAD_GROUPS
        ? List.copyOf(groups.subList(0, MAX_THREAD_GROUPS))
        : groups;
  }

  /** {@code end - start} in whole milliseconds, or null when either reading is missing. */
  static Integer deltaMs(long startNs, long endNs) {
    if (startNs < 0 || endNs < 0) {
      return null;
    }
    return (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toMillis(endNs - startNs));
  }

  private static boolean threadCpuTimeSupported(ThreadMXBean threads) {
    return threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled();
  }

  /**
   * The HotSpot-specific counters, from {@code jdk.management}, in a class of their own so a
   * runtime without that module never links {@code com.sun.management}.
   */
  private static final class HotSpot {
    static final boolean AVAILABLE = ModuleLayer.boot().findModule("jdk.management").isPresent();

    static long[] threadCpuTimes(ThreadMXBean threads, long[] ids) {
      if (threads instanceof com.sun.management.ThreadMXBean hotSpot
          && threadCpuTimeSupported(threads)) {
        return hotSpot.getThreadCpuTime(ids);
      }
      return null;
    }

    private static com.sun.management.OperatingSystemMXBean os() {
      return ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
    }

    static long processCpuTime() {
      return os().getProcessCpuTime();
    }

    /** Machine-wide CPU load from 0 to 1, or negative when not available yet. */
    static double systemCpuLoad() {
      return os().getCpuLoad();
    }

    static long freeMemoryBytes() {
      return os().getFreeMemorySize();
    }
  }
}
