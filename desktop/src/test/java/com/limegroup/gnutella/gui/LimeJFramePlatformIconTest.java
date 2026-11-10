/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The platform icon of a frame is set by a native call that reads FrostWire.exe and waits for the
 * window's thread, so it must not run on the thread that shows the frame
 * (frostwire/frostwire#1522).
 */
class LimeJFramePlatformIconTest {
  @Test
  void theIconIsSetByTheBackgroundExecutorNotByTheCaller() {
    List<Runnable> queued = new ArrayList<>();
    List<Component> iconSetOn = new ArrayList<>();
    List<File> iconFiles = new ArrayList<>();
    Component frame = new JPanel();

    LimeJFrame.setPlatformIcon(
        frame,
        queued::add,
        (component, file) -> {
          iconSetOn.add(component);
          iconFiles.add(file);
        });

    assertEquals(1, queued.size(), "the call is handed to the background executor");
    assertEquals(0, iconSetOn.size(), "nothing ran on the calling thread");

    queued.get(0).run();

    assertEquals(1, iconSetOn.size());
    assertSame(frame, iconSetOn.get(0));
    assertEquals(GUIConstants.FROSTWIRE_EXE_FILE, iconFiles.get(0));
  }
}
