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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.GUIMediator;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author gubatron
 * @author aldenml
 */
final class SearchTabbedPane extends JTabbedPane {
    SearchTabbedPane() {
    }

    @Override
    public void addTab(String title, Icon icon, Component component) {
        super.addTab(title, icon, component);
        int tabIndex = getTabCount() - 1;
        setTabComponentAt(tabIndex, new SearchTabHeader(component, title));
    }

    @Override
    public void setTitleAt(int index, String title) {
        Component c = getTabComponentAt(index);
        if (c instanceof SearchTabHeader) {
            ((SearchTabHeader) c).setTitle(title);
        }
    }

    void setProgressActiveAt(int index, boolean active) {
        Component c = getTabComponentAt(index);
        if (c instanceof SearchTabHeader) {
            ((SearchTabHeader) c).setProgressActive(active);
        }
    }

    void switchToTabByOffset(int offset) {
        int oldIndex = (getSelectedIndex() < 0) ? 0 : getSelectedIndex();
        int newIndex = (oldIndex + offset) % getTabCount();
        //java's modulo will return negative numbers... damn you Gosling.
        if (newIndex < 0) {
            newIndex += getTabCount();
        }
        setSelectedIndex(newIndex);
    }

    private final class SearchTabHeader extends JPanel {
        private final Component component;
        private final JLabel labelText;

        SearchTabHeader(Component component, String text) {
            this.component = component;
            setOpaque(false);
            setLayout(new MigLayout("insets 0, gap 0"));
            JButton buttonClose = new JButton(CancelSearchIconProxy.createSelected());
            buttonClose.setOpaque(false);
            buttonClose.setContentAreaFilled(false);
            buttonClose.setBorderPainted(false);
            buttonClose.addActionListener(new CloseActionHandler());
            add(buttonClose, "h 17!, w 23!");
            labelText = new JLabel(text.trim());
            labelText.setOpaque(false);
            labelText.setHorizontalTextPosition(SwingConstants.LEADING);
            labelText.setAlignmentX(SwingConstants.RIGHT);
            labelText.setIcon(GUIMediator.getThemeImage("indeterminate_small_progress"));
            add(labelText);
        }

        void setTitle(String title) {
            labelText.setText(title);
        }

        void setProgressActive(boolean active) {
            if (active) {
                labelText.setIcon(GUIMediator.getThemeImage("indeterminate_small_progress"));
            } else {
                labelText.setIcon(null);
            }
        }

        class CloseActionHandler implements ActionListener {
            CloseActionHandler() {
            }

            public void actionPerformed(ActionEvent evt) {
                int index = SearchMediator.getSearchResultDisplayer().getIndexForTabComponent(component);
                if (index != -1) {
                    SearchMediator.getSearchResultDisplayer().killSearchAtIndex(index);
                }
            }
        }
    }
}
