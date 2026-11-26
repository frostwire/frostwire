/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.settings.ApplicationSettings;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/**
 * Full or Lite: how much UI work FrostWire does (frostwire/frostwire#1555).
 *
 * <p>Lite turns off UI work only: animations, cover art until asked, live re-sorting of search
 * results while they arrive, preview tooltips, the Pieces hive's frequent re-render, fast table
 * refreshes and the warm-up's background font sweep. Downloading, seeding, searching, IceBridge and
 * the engine are the same in every mode.
 *
 * <p>The user chooses Automatic (the default), Full or Lite in Options &gt; Advanced &gt;
 * Performance. Automatic decides once at startup, from the machine ({@link #isWeak}), and never
 * switches during the session. Full and Lite apply right away when chosen.
 */
public final class PerformanceMode {
  private static final Logger LOG = Logger.getLogger(PerformanceMode.class);

  /** What the user chose. */
  public enum Choice {
    AUTOMATIC,
    FULL,
    LITE;

    /** The setting's value as a choice; Automatic for anything unknown. */
    public static Choice parse(String value) {
      if (value != null) {
        try {
          return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
          // fall through
        }
      }
      return AUTOMATIC;
    }
  }

  /** The mode in effect. */
  public enum Mode {
    FULL,
    LITE
  }

  /**
   * 1-2 CPUs: most process-wide stall reports come from 2-CPU machines (frostwire/frostwire#1512)
   * and 1-2 CPU ones (#1552), where the EDT got 15-50 ms of CPU in a 2.5 s window.
   */
  static final int WEAK_MAX_CPUS = 2;

  /** Less physical memory than this (MB) is a weak machine. */
  static final long WEAK_TOTAL_MEMORY_MB = 4096;

  /**
   * Less free physical memory than this (MB) at startup: the stalling machines in #1512 and #1552
   * had 42-200 MB free. Only read on Windows, where it is the memory available to programs; macOS
   * and Linux count their file cache as used, so a healthy Mac reports a few dozen MB free.
   */
  static final long WEAK_FREE_MEMORY_MB = 1024;

  /**
   * A warm-up whose JDK steps took this long (ms) last time. On a healthy machine the whole warm-up
   * takes about 3 s (Icebase #3dac5bbc1cfe: 8 CPUs, 3,249 ms); on the starved machines of #1512 it
   * took 50 to 250 s (Icebase #0207d9b0209b: 209,687 ms). The JDK steps are timed alone because the
   * whole warm-up also waits for the main window (and the first-run wizard) and, in Lite, skips the
   * font sweep, so its length differs by mode; the JDK steps run the same in both.
   */
  static final long SLOW_WARM_UP_MS = 30_000;

  /**
   * 3-4 CPUs with a Java heap this small (MB) or smaller is weak too. The Windows launcher fixes
   * the heap at 512 MB whatever the memory, and the 4-CPU machines with it that ran Full in Icebase
   * reports (frostwire/frostwire#1512: warm-up 64 to 120 s, three collections in a 2.9 s window,
   * the EDT using 15-46 ms of it) did better as Lite.
   */
  static final long WEAK_SMALL_HEAP_MB = 512;

  static final int WEAK_SMALL_HEAP_MAX_CPUS = 4;

  /** What Automatic looks at. A value below 0 is unknown and does not count. */
  public record MachineFacts(
      int cpus, long totalMemoryMb, long freeMemoryMb, long lastWarmUpMs, long maxHeapMb) {
    /** Facts without the heap size, which is then unknown. */
    public MachineFacts(int cpus, long totalMemoryMb, long freeMemoryMb, long lastWarmUpMs) {
      this(cpus, totalMemoryMb, freeMemoryMb, lastWarmUpMs, -1);
    }

    /** This machine now, with the warm-up time recorded last session. Reads the OS: off the EDT. */
    static MachineFacts current(long lastWarmUpMs) {
      long total = -1;
      long free = -1;
      try {
        com.sun.management.OperatingSystemMXBean os =
            ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        total = os.getTotalMemorySize() / (1024 * 1024);
        if (OSUtils.isWindows()) {
          free = os.getFreeMemorySize() / (1024 * 1024);
        }
      } catch (Throwable t) {
        LOG.info("PerformanceMode: memory size not available: " + t);
      }
      return new MachineFacts(
          Runtime.getRuntime().availableProcessors(),
          total,
          free,
          lastWarmUpMs > 0 ? lastWarmUpMs : -1,
          Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }
  }

  private record State(Choice choice, Mode mode, MachineFacts facts) {}

  /** Full until {@link #resolveAtStartup()}: what tests and early startup code see. */
  private static volatile State state = new State(Choice.FULL, Mode.FULL, null);

  private PerformanceMode() {}

  /** Whether a machine is weak enough for Automatic to pick Lite. */
  static boolean isWeak(MachineFacts facts) {
    return facts.cpus() <= WEAK_MAX_CPUS
        || (facts.totalMemoryMb() >= 0 && facts.totalMemoryMb() < WEAK_TOTAL_MEMORY_MB)
        || (facts.freeMemoryMb() >= 0 && facts.freeMemoryMb() < WEAK_FREE_MEMORY_MB)
        || facts.lastWarmUpMs() >= SLOW_WARM_UP_MS
        || (facts.maxHeapMb() >= 0
            && facts.maxHeapMb() <= WEAK_SMALL_HEAP_MB
            && facts.cpus() <= WEAK_SMALL_HEAP_MAX_CPUS);
  }

  /**
   * Reads the user's choice and, for Automatic, the machine, and fixes the mode for the session.
   * Call once at startup, after the settings load and off the EDT.
   */
  public static void resolveAtStartup() {
    Choice choice = Choice.parse(ApplicationSettings.PERFORMANCE_MODE.getValue());
    MachineFacts facts =
        choice == Choice.AUTOMATIC
            ? MachineFacts.current(ApplicationSettings.LAST_WARM_UP_JDK_STEPS_MS.getValue())
            : null;
    resolve(choice, facts);
    LOG.info("PerformanceMode: " + describe() + (facts == null ? "" : " for " + facts));
  }

  /**
   * Sets the mode for {@code choice}; Automatic decides from {@code facts} (Full without them).
   * Startup and tests call it; tests put it back with {@code resolve(Choice.FULL, null)}.
   */
  public static void resolve(Choice choice, MachineFacts facts) {
    Mode mode =
        switch (choice) {
          case FULL -> Mode.FULL;
          case LITE -> Mode.LITE;
          case AUTOMATIC -> facts != null && isWeak(facts) ? Mode.LITE : Mode.FULL;
        };
    state = new State(choice, mode, facts);
  }

  /**
   * The user chose {@code choice} in Options: saves it, and Full or Lite apply now. Automatic keeps
   * the machine reading taken at startup; when there was none (the session started in Full or
   * Lite), the current mode stays until the next start, when Automatic decides.
   */
  public static void choose(Choice choice) {
    ApplicationSettings.PERFORMANCE_MODE.setValue(choice.name());
    State current = state;
    if (choice == Choice.AUTOMATIC && current.facts() == null) {
      state = new State(choice, current.mode(), null);
    } else {
      resolve(choice, current.facts());
    }
  }

  /** The startup warm-up's JDK steps took {@code ms}: Automatic looks at it next start. */
  public static void warmUpJdkStepsFinished(long ms) {
    ApplicationSettings.LAST_WARM_UP_JDK_STEPS_MS.setValue(Math.max(1, ms));
  }

  public static boolean isLite() {
    return state.mode() == Mode.LITE;
  }

  public static Mode effective() {
    return state.mode();
  }

  /** Whether the user left it on Automatic. */
  public static boolean isAutomatic() {
    return state.choice() == Choice.AUTOMATIC;
  }

  public static Choice choice() {
    return state.choice();
  }

  /** "lite" or "full", for Icebase reports. */
  public static String reportValue() {
    return state.mode().name().toLowerCase(Locale.ROOT);
  }

  private static String describe() {
    return reportValue() + (isAutomatic() ? " (automatic)" : " (chosen)");
  }
}
