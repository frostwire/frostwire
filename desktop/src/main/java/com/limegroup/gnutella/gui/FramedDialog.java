/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import javax.swing.*;
import java.awt.*;

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

    public void showDialog() {
        toFront();
        setVisible(true);
        dialog.toFront();
        // Schedule dialog visibility change on EDT to prevent blocking
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(true);
            dispose();
        });
    }

    public JDialog getDialog() {
        return dialog;
    }
}
