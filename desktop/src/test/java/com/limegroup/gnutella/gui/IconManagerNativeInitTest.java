/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

/**
 * The native file icon controller is built after IconManager, through the executor it is given (the
 * EDT in production: on macOS it builds a JFileChooser, frostwire/frostwire#1361).
 */
class IconManagerNativeInitTest {

  @Test
  void nativeControllerIsBuiltLaterAndThenUsed() {
    Icon nativeIcon = new ImageIcon();
    List<Runnable> later = new ArrayList<>();
    IconManager manager = new IconManager(true, () -> new FakeController(nativeIcon), later::add);
    assertEquals(1, later.size(), "the native controller must be built through the executor");
    assertTrue(manager.getIconForExtension("mp3") != nativeIcon, "basic icons until it is built");
    later.get(0).run();
    assertSame(nativeIcon, manager.getIconForExtension("mp3"));
  }

  @Test
  void anInvalidNativeControllerKeepsTheBasicOne() {
    Icon nativeIcon = new ImageIcon();
    IconManager manager =
        new IconManager(true, () -> new FakeController(nativeIcon, false), Runnable::run);
    assertTrue(manager.getIconForExtension("mp3") != nativeIcon);
  }

  @Test
  void platformsWithoutNativeIconsNeverBuildOne() {
    AtomicBoolean built = new AtomicBoolean();
    new IconManager(
        false,
        () -> {
          built.set(true);
          return new FakeController(new ImageIcon());
        },
        Runnable::run);
    assertFalse(built.get());
  }

  private static final class FakeController implements FileIconController {
    private final Icon icon;
    private final boolean valid;

    FakeController(Icon icon) {
      this(icon, true);
    }

    FakeController(Icon icon, boolean valid) {
      this.icon = icon;
      this.valid = valid;
    }

    @Override
    public Icon getIconForFile(File f) {
      return icon;
    }

    @Override
    public Icon getIconForExtension(String ext) {
      return icon;
    }

    @Override
    public boolean isIconForFileAvailable(File f) {
      return true;
    }

    @Override
    public boolean isValid() {
      return valid;
    }
  }
}
