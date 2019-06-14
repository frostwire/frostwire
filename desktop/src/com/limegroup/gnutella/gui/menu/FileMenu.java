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

package com.limegroup.gnutella.gui.menu;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.FileMenuActions;
import org.limewire.util.OSUtils;

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
        MENU.add(createMenuItem(new FileMenuActions.OpenMagnetTorrentAction()));
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
