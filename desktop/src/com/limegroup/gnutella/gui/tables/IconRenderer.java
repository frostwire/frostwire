/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
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
 * Renders the column in the search window that displays an icon for
 * whether or not the host returning the result is chattable.
 */
public final class IconRenderer extends DefaultTableBevelledCellRenderer {
    /**
     *
     */
    private static final long serialVersionUID = 8144602599802586291L;

    /**
     * The constructor sets this <tt>JLabel</tt> to be opaque and sets the
     * border.
     */
    public IconRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    /**
     * Returns the <tt>Component</tt> that displays the stars based
     * on the number of stars in the <tt>QualityHolder</tt> object.
     */
    public Component getTableCellRendererComponent
    (JTable table, Object value, boolean isSelected,
     boolean hasFocus, int row, int column) {
        setIcon((Icon) value);
        return super.getTableCellRendererComponent(
                table, null, isSelected, hasFocus, row, column);
    }
}
