/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Image;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MainSplashImageTest {

  /**
   * The startup thread decodes the splash PNG once (for the AWT splash, or in
   * Initializer.switchSplashes); the Swing splash, built on the EDT, gets that same decoded image
   * instead of decoding it again there.
   */
  @Test
  void theSplashImageIsDecodedOnceAndTheEdtGetsTheSameInstance() throws Exception {
    AtomicReference<Image> decoded = new AtomicReference<>();
    Thread startup = new Thread(() -> decoded.set(Main.getChosenSplashImage()));
    startup.start();
    startup.join();
    assertNotNull(decoded.get());

    AtomicReference<Image> onEdt = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> onEdt.set(Main.getChosenSplashImage()));

    assertSame(decoded.get(), onEdt.get());
  }
}
