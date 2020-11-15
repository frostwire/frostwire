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

package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.tables.DefaultTableBevelledCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Creates both a renderer and an editor for cells in the playlist table that display the name
 * of the file being played.
 *
 * @author gubatron
 * @author aldenml
 */
class PlaylistItemStarRenderer extends DefaultTableBevelledCellRenderer {
    private static final long serialVersionUID = 6800146830099830381L;
    private static final Icon starOn;
    private static final Icon starOff;
    private static final Icon exclamation;

    static {
        starOn = GUIMediator.getThemeImage("star_on");
        starOff = GUIMediator.getThemeImage("star_off");
        exclamation = GUIMediator.getThemeImage("exclamation");
    }

    public PlaylistItemStarRenderer() {
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        final LibraryPlaylistsTableDataLine line = ((PlaylistItemProperty<?>) value).getLine();
        final PlaylistItemProperty<?> cell = (PlaylistItemProperty<?>) value;
        setIcon(line.getPlayListItem().isStarred(), cell.exists());
        final JLabel component = (JLabel) super.getTableCellRendererComponent(table, null, isSelected, hasFocus, row, column);
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setIcon(line.getPlayListItem().isStarred(), cell.exists());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setIcon(line.getPlayListItem().isStarred(), cell.exists());
            }
        });
        return component;
    }

    private void setIcon(boolean starred, boolean exists) {
        if (!exists) {
            setIcon(exclamation);
        } else if (starred) {
            setIcon(starOn);
        } else {
            setIcon(starOff);
        }
    }
}