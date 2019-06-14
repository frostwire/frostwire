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

package com.frostwire.gui.bittorrent;

import com.limegroup.gnutella.gui.ButtonRow;

/**
 * This class contains the buttons in the download window, allowing
 * classes in this package to enable or disable buttons at specific
 * indexes in the row.
 */
final class BTDownloadButtons {
    /**
     * The row of buttons for the download window.
     */
    private final ButtonRow BUTTONS;

    BTDownloadButtons(final BTDownloadMediator dm) {
        BUTTONS = new ButtonRow(dm.getActions(), ButtonRow.X_AXIS, ButtonRow.NO_GLUE);
    }

    ButtonRow getComponent() {
        return BUTTONS;
    }
}
