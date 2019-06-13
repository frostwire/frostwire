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

package com.limegroup.gnutella.gui.tables;

import javax.swing.*;
import java.awt.*;

/**
 * Renders an icon along with a label.
 *
 * @author gubatron
 * @author aldenml
 */
public final class ActionIconAndNameRenderer extends DefaultTableBevelledCellRenderer {
    /**
     *
     */
    private static final long serialVersionUID = 8244672573299436077L;

    /**
     * Constructs a new IconAndNameRenderer with the Icon aligned to the left
     * of the text, with a text gap of 5 between the icon and text.
     */
    public ActionIconAndNameRenderer() {
        super();
        setHorizontalAlignment(LEFT);
        setIconTextGap(5);
        setHorizontalTextPosition(RIGHT);
    }

    /**
     * Returns the <tt>Component</tt> that displays the icons & names
     * based on the <tt>IconAndNameHolder</tt> object.
     */
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        ActionIconAndNameHolder in = (ActionIconAndNameHolder) value;
        Icon icon = null;
        String name = null;
        if (in != null) {
            icon = in.getIcon();
            name = in.getName();
            if (name != null) {
                String strValue = name;
                strValue = strValue.replace("<html>", "<html><div width=\"1000000px\">");
                strValue = strValue.replace("</html>", "</div></html>");
                name = strValue;
            }
        }
        setIcon(icon);
        return super.getTableCellRendererComponent(table, name, isSelected, hasFocus, row, column);
    }
}
