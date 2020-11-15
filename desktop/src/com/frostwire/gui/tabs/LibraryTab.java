/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.tabs;

import com.frostwire.gui.library.LibraryMediator;
import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import java.awt.*;

/**
 * This class handles access to the tab that contains the library
 * as well as the playlist to the user.
 *
 * @author gubatron
 * @author aldenml
 */
public final class LibraryTab extends AbstractTab {
    private static JPanel PANEL;
    private static LibraryMediator LIBRARY_MEDIATOR;

    /**
     * Constructs the elements of the tab.
     *
     * @param lm the <tt>LibraryMediator</tt> instance
     */
    public LibraryTab(LibraryMediator lm) {
        super(I18n.tr("Library"), I18n.tr("Browse, Search and Play files in your computer. Wi-Fi sharing, Internet Radio and more."), "library_tab");
        LIBRARY_MEDIATOR = lm;
    }

    private static JPanel getPanel() {
        if (PANEL == null) {
            PANEL = createPanel();
        }
        return PANEL;
    }

    private static JPanel createPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        /*
          Constant for the <tt>Component</tt> instance containing the
          elements of this tab.
         */
        JComponent COMPONENT = LIBRARY_MEDIATOR.getComponent();
        panel.add(COMPONENT, BorderLayout.CENTER);
        panel.invalidate();
        panel.validate();
        return panel;
    }

    public JComponent getComponent() {
        return getPanel();
    }
}