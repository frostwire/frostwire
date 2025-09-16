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
