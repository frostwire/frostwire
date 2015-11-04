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

import java.awt.BorderLayout;

import javax.swing.JTable;

import com.frostwire.gui.LocaleLabel;
import com.limegroup.gnutella.gui.search.FWAbstractJPanelTableCellRenderer;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public final class NameHolderRenderer extends FWAbstractJPanelTableCellRenderer {

    private LocaleLabel labelText;

    public NameHolderRenderer() {
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        labelText = new LocaleLabel();
        add(labelText, BorderLayout.CENTER);
    }

    private void setData(NameHolder value, JTable table, int row) {
        labelText.setText(value.getLocaleString());
        syncFontSize(table, labelText);
    }

    @Override
    protected void updateUIData(Object value, JTable table, int row, int column) {
        setData((NameHolder) value, table, row);
    }
}