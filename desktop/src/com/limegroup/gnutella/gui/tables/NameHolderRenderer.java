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

import com.frostwire.gui.LocaleLabel;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class NameHolderRenderer extends FWAbstractJPanelTableCellRenderer {
    private LocaleLabel labelText;

    NameHolderRenderer() {
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(1, 5, 1, 5));
        labelText = new LocaleLabel();
        add(labelText, BorderLayout.CENTER);
    }

    private void setData(NameHolder value, JTable table) {
        if (labelText != null) {
            if (value != null) {
                labelText.setText(value.getLocaleString());
            }
            if (table != null) {
                syncFontSize(table, labelText);
            }
        }
    }

    @Override
    protected void updateUIData(Object value, JTable table, int row, int column) {
        setData((NameHolder) value, table);
    }
}