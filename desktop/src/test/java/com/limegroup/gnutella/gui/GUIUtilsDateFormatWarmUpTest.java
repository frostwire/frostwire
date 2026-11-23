/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The date format the date cells use is built off the EDT at startup, so the first date cell does
 * not load the app language's date data while painting (Icebase #eb4413b6ce97,
 * frostwire/frostwire#1563).
 */
class GUIUtilsDateFormatWarmUpTest {

  @Test
  void warmingUpOffTheEdtBuildsTheFormatTheCellsLaterReuse() throws Exception {
    AtomicReference<Boolean> onEdt = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              onEdt.set(SwingUtilities.isEventDispatchThread());
              GUIUtils.warmUpDateTimeFormat();
            },
            "date-format-warm-up-test");
    worker.start();
    worker.join();

    assertFalse(onEdt.get());
    DateFormat first = GUIUtils.getFullDateTimeFormat();
    assertNotNull(first);
    assertSame(first, GUIUtils.getFullDateTimeFormat());
  }

  @Test
  void startupWarmsTheDateFormatOnAParallelExecutorNotTheEdt() throws IOException {
    String initializer =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        initializer.contains("DesktopParallelExecutor.execute(GUIUtils::warmUpDateTimeFormat);"));
  }
}
