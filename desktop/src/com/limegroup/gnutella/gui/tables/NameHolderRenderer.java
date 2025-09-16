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