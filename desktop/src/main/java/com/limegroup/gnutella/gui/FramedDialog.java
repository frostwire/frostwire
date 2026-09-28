/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import java.awt.*;
import javax.swing.*;

public class FramedDialog extends LimeJFrame {
  private final JDialog dialog = new JDialog(this);

  public FramedDialog() throws HeadlessException {
    super();
    initialize();
  }

  private void initialize() {
    setUndecorated(true);
    setSize(0, 0);
  }

  /**
   * Shows the dialog modally on the EDT and disposes the owner frame only once the dialog has
   * closed. Disposing the owner earlier also disposes the dialog, which made the setup wizard
   * vanish the instant it appeared: a non-modal {@code setVisible(true)} returns immediately.
   */
  public void showDialog() {
    toFront();
    setVisible(true);
    dialog.toFront();
    dialog.pack();
    SwingUtilities.invokeLater(
        () -> {
          try {
            dialog.setVisible(true); // blocks (nested event loop) while the dialog is modal
          } finally {
            dispose();
          }
        });
  }

  public JDialog getDialog() {
    return dialog;
  }
}
