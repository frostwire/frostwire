/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.gui.theme.ThemeMediator.ThemeEnum;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.DamagedInstallation;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * What happens when the theme cannot be set up at startup (frostwire/frostwire#1576, Icebase
 * #393c5f668c41): the failure names the theme and the step, keeps its cause, and a FlatLaf theme
 * that fails starts the default theme instead of not starting at all.
 */
final class ThemeStartup {
  private static final Logger LOG = Logger.getLogger(ThemeStartup.class);

  /** The theme that failed and was replaced by the default one, null when nothing fell back. */
  private static final AtomicReference<String> FALLBACK_FROM = new AtomicReference<>();

  private ThemeStartup() {}

  /**
   * The name of the theme that could not be loaded at startup and was replaced by the default
   * theme, or null when no fallback happened. The first fallback of the launch is the one kept.
   */
  static String fallbackFrom() {
    return FALLBACK_FROM.get();
  }

  /** Forgets the recorded fallback; for tests only. */
  static void resetFallback() {
    FALLBACK_FROM.set(null);
  }

  /** Applies a theme at startup; names each step it starts so a failure says where it was. */
  interface Applier {
    void apply(ThemeEnum theme, Consumer<String> step) throws Exception;
  }

  /**
   * Loads the chosen theme; when a FlatLaf theme cannot be set up, logs the failure, hands the
   * failure to {@code reportFailure} (the crash report) and applies the default theme. The user's
   * saved choice is left alone by the caller, so a failure that is gone next launch does not cost
   * them their theme. When the default theme cannot load either, or the failure says FrostWire's
   * files are damaged, throws with the theme and the step that failed, and the failure as the
   * cause.
   */
  static void load(ThemeEnum theme, Applier applier, Consumer<Throwable> reportFailure) {
    String[] step = {"starting"};
    try {
      applier.apply(theme, name -> step[0] = name);
      return;
    } catch (Exception | LinkageError e) {
      RuntimeException failure =
          new RuntimeException(
              "Unable to load theme " + theme + " at startup, failed at step: " + step[0], e);
      if (theme == ThemeEnum.DEFAULT || DamagedInstallation.isDamaged(e)) {
        LOG.error(failure.getMessage(), failure);
        throw failure;
      }
      LOG.error(failure.getMessage() + "; starting with the default theme", failure);
      // The report keeps only the exception's own class and frames, never its cause, so it gets
      // the real failure, not the wrapper that would hide it.
      reportFailure.accept(e);
    }
    String[] fallbackStep = {"starting"};
    try {
      applier.apply(ThemeEnum.DEFAULT, name -> fallbackStep[0] = name);
    } catch (Exception | LinkageError e) {
      RuntimeException failure =
          new RuntimeException(
              "Unable to load theme "
                  + theme
                  + " at startup, nor the default theme (failed at step: "
                  + fallbackStep[0]
                  + ")",
              e);
      LOG.error(failure.getMessage(), failure);
      throw failure;
    }
    FALLBACK_FROM.compareAndSet(null, theme.name());
  }
}
