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

import org.limewire.util.OSUtils;
import org.limewire.util.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A JFrame that uses LimeWire's icon.
 */
public class LimeJFrame extends JFrame {
    public LimeJFrame() throws HeadlessException {
        super();
        initialize();
    }

    public LimeJFrame(GraphicsConfiguration gc) {
        super(gc);
        initialize();
    }

    public LimeJFrame(String title, GraphicsConfiguration gc) {
        super(title, gc);
        initialize();
    }

    public LimeJFrame(String title) throws HeadlessException {
        super(title);
        initialize();
    }

    private static List<JPopupMenu> getPopups() {
        MenuSelectionManager msm = MenuSelectionManager.defaultManager();
        MenuElement[] p = msm.getSelectedPath();
        List<JPopupMenu> list = new ArrayList<>(p.length);
        for (MenuElement element : p) {
            if (element instanceof JPopupMenu) {
                list.add((JPopupMenu) element);
            }
        }
        return list;
    }

    private void initialize() {
        ImageIcon limeIcon = GUIMediator.getThemeImage(GUIConstants.FROSTWIRE_64x64_ICON);
        setIconImage(limeIcon.getImage());
        if (OSUtils.isMacOSX()) {
            setupPopupHide();
        }
    }

    // Overrides addNotify() to change to a platform specific icon right afterwards.
    @Override
    public void addNotify() {
        super.addNotify();
        // Replace the Swing icon with a prettier platform-specific one
        SystemUtils.setWindowIcon(this, GUIConstants.FROSTWIRE_EXE_FILE);
    }

    private void setupPopupHide() {
        addWindowListener(new WindowAdapter() {
            public void windowDeactivated(WindowEvent e) {
                for (JPopupMenu menu : getPopups()) {
                    menu.setVisible(false);
                }
            }
        });
    }
}
