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

package com.limegroup.gnutella.gui.actions;

import com.limegroup.gnutella.gui.GUIMediator;

import java.awt.event.ActionEvent;

/**
 * Opens the given url in a browser.
 */
public class OpenLinkAction extends AbstractAction {
    /**
     *
     */
    private static final long serialVersionUID = -7243267672146519961L;
    private final String url;

    public OpenLinkAction(String url, String name) {
        this(url, name, null);
    }

    public OpenLinkAction(String url, String name, String description) {
        super(name);
        this.url = url;
        putValue(LONG_DESCRIPTION, description);
    }

    public void actionPerformed(ActionEvent e) {
        GUIMediator.openURL(url);
    }
}
