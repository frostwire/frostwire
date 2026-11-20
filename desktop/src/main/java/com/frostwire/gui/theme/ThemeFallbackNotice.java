/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.limegroup.gnutella.gui.I18n;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.limewire.util.CommonUtils;

/**
 * The one-time notice that FrostWire started with the default theme because the user's theme could
 * not be loaded (frostwire/frostwire#1576). Pure logic with no Swing: the main window asks {@link
 * #takeMessage()} once it is visible and shows whatever text comes back, plain text, at most once
 * per launch and only when a fallback happened.
 */
public final class ThemeFallbackNotice {
  private static final ThemeFallbackNotice INSTANCE =
      new ThemeFallbackNotice(
          ThemeStartup::fallbackFrom, () -> CommonUtils.getUserSettingsDir().getAbsolutePath());

  private final Supplier<String> failedTheme;
  private final Supplier<String> logLocation;
  private final AtomicBoolean taken = new AtomicBoolean();

  ThemeFallbackNotice(Supplier<String> failedTheme, Supplier<String> logLocation) {
    this.failedTheme = failedTheme;
    this.logLocation = logLocation;
  }

  /** The notice text for this launch, or null when no fallback happened or it was already taken. */
  public static String takeMessage() {
    return INSTANCE.take();
  }

  String take() {
    String theme = failedTheme.get();
    if (theme == null || !taken.compareAndSet(false, true)) {
      return null;
    }
    return I18n.tr(
        "FrostWire started with the default theme because your theme {0} could not be loaded. Details are logged in {1}",
        theme, logLocation.get());
  }
}
