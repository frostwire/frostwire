/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Every SkinMenu swaps JMenu's private popup menu through reflection, and the JVM generated that
 * field's accessor (defining classes) on the EDT inside the first right-click (Icebase
 * #3ebacc1f117a, GitHub #1439). The startup warm-up builds it off the EDT instead.
 *
 * <p>The JVM's accessor cache can't be observed from a test (java.lang.reflect.Field's own fields
 * are filtered from reflection), so the wiring into JdkWarmUp is checked in its source.
 */
class SkinMenuWarmUpTest {

  @Test
  void warmUpRunsOffTheEdtAndMenusStillGetTheSkinPopup() throws Exception {
    SkinMenu.warmUpPopupMenuAccess();
    SkinMenu.warmUpPopupMenuAccess();

    AtomicReference<SkinMenu> menu = new AtomicReference<>();
    AtomicReference<JPopupMenu> popup = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          menu.set(new SkinMenu("fixture"));
          popup.set(menu.get().getPopupMenu());
        });

    assertInstanceOf(SkinPopupMenu.class, popup.get());
    assertSame(menu.get(), popup.get().getInvoker());
  }

  @Test
  void startupWarmUpBuildsTheMenuAccessor() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/frostwire/util/JdkWarmUp.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    int warmUp = source.indexOf("privatestaticvoidwarmUp(){");
    assertTrue(warmUp >= 0);
    assertTrue(
        source.indexOf("SkinMenu.warmUpPopupMenuAccess();", warmUp) > warmUp,
        "JdkWarmUp must build SkinMenu's reflective accessor off the EDT");
  }
}
