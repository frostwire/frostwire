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

package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * Wraps a {@link CheckBoxList} into a scroll pane and shows its
 * {@link CheckBoxList#getActions() actions} in a right aligned button
 * row underneath.
 */
public class CheckBoxListPanel<E> extends BoxPanel {
    private final CheckBoxList<E> list;

    /**
     * Constructs a checkbox list panel for an array of objects.
     * <p>
     */
    public CheckBoxListPanel(Collection<E> elements, CheckBoxList.TextProvider<E> provider,
                             boolean selected) {
        list = new CheckBoxList<>(elements, provider, selected, CheckBoxList.SELECT_FIRST_OFF);
        initialize();
    }

    private void initialize() {
        InternalJScrollPane scrollPane = new InternalJScrollPane(list);
        scrollPane.getViewport().setBackground(UIManager.getColor("List.background"));
        add(scrollPane);
        add(Box.createVerticalStrut(ButtonRow.BUTTON_SEP));
        add(new ButtonRow(list.getActions(), ButtonRow.X_AXIS, ButtonRow.LEFT_GLUE));
    }

    /**
     * Returns a typed array of the selected objects.
     */
    public List<E> getSelectedElements() {
        return list.getCheckedElements();
    }

    /**
     * Returns the checkbox list used internally.
     */
    public CheckBoxList<E> getList() {
        return list;
    }

    /**
     * Inherit from JScrollPane just to override updateUI.
     */
    private class InternalJScrollPane extends JScrollPane {
        /**
         *
         */
        private static final long serialVersionUID = 5346177338334373472L;

        InternalJScrollPane(Component comp) {
            super(comp);
            getViewport().setBackground(UIManager.getColor("List.background"));
        }

        public void updateUI() {
            super.updateUI();
            getViewport().setBackground(UIManager.getColor("List.background"));
        }
    }
}
