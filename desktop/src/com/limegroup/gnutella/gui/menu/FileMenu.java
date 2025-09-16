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

package com.limegroup.gnutella.gui.menu;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.FileMenuActions;
import com.frostwire.util.OSUtils;

import javax.swing.*;

/**
 * Handles all of the contents of the file menu in the menu bar.
 */
final class FileMenu extends AbstractMenu {
    /**
     * Creates a new <tt>FileMenu</tt>, using the <tt>key</tt>
     * argument for setting the locale-specific title and
     * accessibility text.
     *
     */
    FileMenu() {
        super(I18n.tr("&File"));
        MENU.add(createMenuItem(new FileMenuActions.SendFileAction()));
        MENU.addSeparator();
        MENU.add(createMenuItem(new FileMenuActions.OpenMagnetTorrentVideoUrlAction()));
        MENU.add(createMenuItem(new FileMenuActions.CreateTorrentAction()));
        if (!OSUtils.isMacOSX()) {
            MENU.addSeparator();
            MENU.add(createMenuItem(new FileMenuActions.CloseAction()));
            MENU.add(createMenuItem(new FileMenuActions.ExitAction()));
        }
    }

    /**
     * Returns a new <tt>JMenuItem</tt> instance that is configured from
     * the action.
     */
    private JMenuItem createMenuItem(Action action) {
        return new JMenuItem(action);
    }
}
