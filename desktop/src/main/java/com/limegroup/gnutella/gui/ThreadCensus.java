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

/**
 * How many threads the process runs and which kinds, for Icebase reports
 * (frostwire/frostwire#1393). An OutOfMemoryError "unable to create native thread" can't be
 * diagnosed without it: it says whether a FrostWire pool leaks threads or the machine ran out of
 * memory.
 *
 * <p>Threads are grouped by {@link #groupName(String) name prefix} only, never by full name: some
 * names carry a search term or a URL after a space or a colon.
 *
 * <p>The Java heap isn't owned by threads, so "memory per thread" is reported as what each group
 * has allocated on the heap since its threads started, plus the stack space all threads reserve.
 * Both need the {@code jdk.management} module; without it those two numbers are left out.
 */
final class ThreadCensus {
  static final int MAX_GROUPS = 8;
  static final int MAX_NAME_LENGTH = 40;

  /** Field names are the report's JSON keys. */
  static final class Group {
    private final String name;
    private final int count;

    /** Null, and then left out of the JSON, when per-thread allocation isn't measured. */
    private final Long alloc_mb;

    Group(String name, int count, Long allocMb) {
      this.name = name;
      this.count = count;
      this.alloc_mb = allocMb;
    }

    String name() {
      return name;
    }

    int count() {
      return count;
    }

    Long allocMb() {
      return alloc_mb;
    }
  }

  final int count;
  final int peak;

  /** Null when the thread stack size can't be read. */
  final Integer stackMb;

  final List<Group> groups;

  private ThreadCensus(int count, int peak, Integer stackMb, List<Group> groups) {
    this.count = count;
    this.peak = peak;
    this.stackMb = stackMb;
    this.groups = groups;
  }

  /**
   * The census now, or null if the JVM's thread management can't be read or there is no memory left
   * to take it: the report it goes into, often about an OutOfMemoryError, matters more.
   */
  static ThreadCensus take() {
    try {
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      long[] ids = threads.getAllThreadIds();
      ThreadInfo[] infos = threads.getThreadInfo(ids, 0);
      long[] allocated = HotSpot.AVAILABLE ? HotSpot.allocatedBytes(threads, ids) : null;
      String[] names = new String[infos.length];
      for (int i = 0; i < infos.length; i++) {
        // Null for a thread that ended after getAllThreadIds.
        names[i] = infos[i] == null ? null : infos[i].getThreadName();
      }
      int count = threads.getThreadCount();
      Integer stackMb = null;
      if (HotSpot.AVAILABLE) {
        long stackKb = HotSpot.threadStackSizeKb();
        if (stackKb > 0) {
          stackMb = (int) Math.min(Integer.MAX_VALUE, count * stackKb / 1024);
        }
      }
      return new ThreadCensus(
          count, threads.getPeakThreadCount(), stackMb, group(names, allocated));
    } catch (RuntimeException | LinkageError | OutOfMemoryError e) {
      return null;
    }
  }

  /**
   * The {@link #MAX_GROUPS} largest groups of threads by {@link #groupName(String)}, most threads
   * first. {@code allocatedBytes} is parallel to {@code names}, or null when not measured; a null
   * name is a thread that ended and isn't counted.
   */
  static List<Group> group(String[] names, long[] allocatedBytes) {
    Map<String, long[]> totals = new HashMap<>();
    for (int i = 0; i < names.length; i++) {
      if (names[i] == null) {
        continue;
      }
      long[] total = totals.computeIfAbsent(groupName(names[i]), k -> new long[2]);
      total[0]++;
      if (allocatedBytes != null && allocatedBytes[i] > 0) {
        total[1] += allocatedBytes[i];
      }
    }
    List<Group> groups = new ArrayList<>();
    totals.forEach(
        (name, total) ->
            groups.add(
                new Group(
                    name,
                    (int) total[0],
                    allocatedBytes == null ? null : total[1] / (1024 * 1024))));
    groups.sort(Comparator.comparingInt(Group::count).reversed().thenComparing(Group::name));
    return groups.size() > MAX_GROUPS ? List.copyOf(groups.subList(0, MAX_GROUPS)) : groups;
  }

  /**
   * The thread name's prefix: each run of digits becomes {@code N}, and it stops at the first
   * character other than a letter, {@code _ . $ -}, at most {@link #MAX_NAME_LENGTH} characters.
   * {@code "pool-3-thread-12"} is {@code "pool-N-thread-N"}, {@code "SuggestionsThread: query"} is
   * {@code "SuggestionsThread"}, {@code "OkHttp https://host/"} is {@code "OkHttp"}. A name that
   * starts with anything else is {@code "unnamed"}.
   */
  static String groupName(String threadName) {
    StringBuilder prefix = new StringBuilder();
    boolean inDigits = false;
    for (int i = 0; i < threadName.length() && prefix.length() < MAX_NAME_LENGTH; i++) {
      char c = threadName.charAt(i);
      if (c >= '0' && c <= '9') {
        if (!inDigits) {
          prefix.append('N');
        }
        inDigits = true;
        continue;
      }
      inDigits = false;
      boolean allowed =
          (c >= 'A' && c <= 'Z')
              || (c >= 'a' && c <= 'z')
              || c == '_'
              || c == '.'
              || c == '$'
              || c == '-';
      if (!allowed) {
        break;
      }
      prefix.append(c);
    }
    return prefix.length() == 0 ? "unnamed" : prefix.toString();
  }

  /**
   * The HotSpot-specific numbers, from {@code jdk.management}. Kept in its own class so that a
   * runtime without that module never links {@code com.sun.management}.
   */
  private static final class HotSpot {
    static final boolean AVAILABLE = ModuleLayer.boot().findModule("jdk.management").isPresent();

    static long[] allocatedBytes(ThreadMXBean threads, long[] ids) {
      if (threads instanceof com.sun.management.ThreadMXBean hotSpot
          && hotSpot.isThreadAllocatedMemorySupported()
          && hotSpot.isThreadAllocatedMemoryEnabled()) {
        return hotSpot.getThreadAllocatedBytes(ids);
      }
      return null;
    }

    /** The default Java thread stack size in KB, or 0 when it is the OS default. */
    static long threadStackSizeKb() {
      try {
        return Long.parseLong(
            ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class)
                .getVMOption("ThreadStackSize")
                .getValue());
      } catch (RuntimeException e) {
        return 0;
      }
    }
  }
}
