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