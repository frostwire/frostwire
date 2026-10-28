/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicFileChooserUI;
import org.junit.jupiter.api.Test;

/**
 * The chooser that lends NativeFileIconController the native file view is built on the EDT and
 * installs only the native UI: the look and feel's chooser UI (our skin's) checks every folder
 * above the home folder on disk while the chooser sets its current directory
 * (frostwire/frostwire#1361).
 */
class NativeUiChooserTest {

  /** Counts installs of the look and feel's chooser UI. */
  public static final class LookAndFeelChooserUI extends BasicFileChooserUI {
    static final AtomicInteger INSTALLS = new AtomicInteger();

    public LookAndFeelChooserUI(JFileChooser chooser) {
      super(chooser);
    }

    public static ComponentUI createUI(JComponent c) {
      return new LookAndFeelChooserUI((JFileChooser) c);
    }

    @Override
    public void installUI(JComponent c) {
      INSTALLS.incrementAndGet();
      super.installUI(c);
    }
  }

  @Test
  void installsOnlyTheNativeUi() throws Exception {
    Object previousUi = UIManager.get("FileChooserUI");
    UIManager.put("FileChooserUI", LookAndFeelChooserUI.class.getName());
    UIManager.put(LookAndFeelChooserUI.class.getName(), LookAndFeelChooserUI.class);
    LookAndFeelChooserUI.INSTALLS.set(0);
    AtomicReference<ComponentUI> nativeUi = new AtomicReference<>();
    AtomicReference<JFileChooser> chooser = new AtomicReference<>();
    try {
      EventQueue.invokeAndWait(
          () ->
              chooser.set(
                  new NativeFileIconController.NativeUiChooser(
                      c -> {
                        nativeUi.set(new BasicFileChooserUI((JFileChooser) c));
                        return nativeUi.get();
                      })));
    } finally {
      UIManager.put("FileChooserUI", previousUi);
      UIManager.put(LookAndFeelChooserUI.class.getName(), null);
    }
    assertEquals(0, LookAndFeelChooserUI.INSTALLS.get(), "the look and feel's UI was installed");
    assertSame(nativeUi.get(), chooser.get().getUI());
    assertNotNull(chooser.get().getFileView());
  }
}
