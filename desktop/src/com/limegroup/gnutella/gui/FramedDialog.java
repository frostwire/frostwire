/*
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

import java.awt.GraphicsConfiguration;
import java.awt.HeadlessException;

import javax.swing.JDialog;

public class FramedDialog extends LimeJFrame {

    private final JDialog dialog = new JDialog(this);

    public FramedDialog() throws HeadlessException {
        super();
        initialize();
    }

    public FramedDialog(GraphicsConfiguration arg0) {
        super(arg0);
        initialize();
    }

    public FramedDialog(String arg0, GraphicsConfiguration arg1) {
        super(arg0, arg1);
        initialize();
    }

    public FramedDialog(String arg0) throws HeadlessException {
        super(arg0);
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
        dialog.setVisible(true);
        dispose();
    }

    public JDialog getDialog() {
        return dialog;
    }

}
