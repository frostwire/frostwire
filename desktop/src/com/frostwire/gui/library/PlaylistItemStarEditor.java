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

import com.frostwire.alexandria.PlaylistItem;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public class PlaylistItemStarEditor extends AbstractCellEditor implements TableCellEditor {
    private static final long serialVersionUID = 2484867032644699734L;
    private static final Icon starOn;
    private static final Icon starOff;
    private static final Icon exclamation;

    static {
        starOn = GUIMediator.getThemeImage("star_on");
        starOff = GUIMediator.getThemeImage("star_off");
        exclamation = GUIMediator.getThemeImage("exclamation");
    }

    public PlaylistItemStarEditor() {
    }

    public Object getCellEditorValue() {
        return null;
    }

    public Component getTableCellEditorComponent(final JTable table, final Object value, boolean isSelected, int row, int column) {
        final LibraryPlaylistsTableDataLine line = ((PlaylistItemProperty<?>) value).getLine();
        final JLabel component = (JLabel) new PlaylistItemStarRenderer().getTableCellRendererComponent(table, value, isSelected, true, row, column);
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!((PlaylistItemProperty<?>) value).exists()) {
                    return;
                }
                PlaylistItem playlistItem = line.getInitializeObject();
                if (line.getInitializeObject().isStarred()) {
                    playlistItem.setStarred(false);
                    playlistItem.save();
                    component.setIcon(starOff);
                } else {
                    playlistItem.setStarred(true);
                    playlistItem.save();
                    component.setIcon(starOn);
                }
            }
        });
        if (!((PlaylistItemStarProperty) value).exists()) {
            component.setIcon(exclamation);
        } else if (line.getInitializeObject().isStarred()) {
            component.setIcon(starOn);
        } else {
            component.setIcon(starOff);
        }
        return component;
    }
}
