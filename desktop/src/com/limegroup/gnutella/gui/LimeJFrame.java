/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;
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
