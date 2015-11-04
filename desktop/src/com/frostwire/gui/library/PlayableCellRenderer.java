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

import java.awt.Component;

import javax.swing.JTable;

import com.limegroup.gnutella.gui.tables.DefaultTableBevelledCellRenderer;

/**
 *  Creates both a renderer and an editor for cells in the playlist table that display the name
 *  of the file being played.
 *  
 *  @author gubatron
 *  @author aldenml
 */
class PlayableCellRenderer extends DefaultTableBevelledCellRenderer {

    public PlayableCellRenderer() {
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        PlayableCell cell = (PlayableCell) value;

        super.getTableCellRendererComponent(table, cell.toString(), isSelected, hasFocus, row, column);
        //setFontColor(cell.isPlaying(), table, row, column);
        return this;
    }

    /**
     * Check what font color to use if this song is playing.
     * We used to color rows differently when they were playing. 
     */
    /*
    private void setFontColor(boolean isPlaying, JTable table, int row, int column) {
        if (isPlaying) {
            setForeground(ThemeMediator.PLAYING_DATA_LINE_COLOR);
        } else {
            setForeground(table.getForeground());
        }
    }
    */
}