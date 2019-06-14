/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchEngine;
import net.miginfocom.swing.MigLayout;
import org.limewire.setting.BooleanSetting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class SearchEnginesPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Search Engines");
    private final static String LABEL = I18n.tr("Select which search engines you want FrostWire to use.");
    private final Map<JCheckBox, BooleanSetting> cBoxes;
    private final List<JCheckBox> searchEngineCheckboxes;
    private final SearchEngineCheckboxListener searchEnginesCheckboxListener;
    private JCheckBox allCheckbox;

    public SearchEnginesPaneItem() {
        super(TITLE, LABEL);
        searchEngineCheckboxes = new LinkedList<>();
        cBoxes = new HashMap<>();
        searchEnginesCheckboxListener = new SearchEngineCheckboxListener(cBoxes);
        add(createSearchEnginesCheckboxPanel());
        add(createToggleAllCheckbox());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
    }

    public boolean applyOptions() {
        return false;
    }

    public boolean isDirty() {
        return false;
    }

    private Component createToggleAllCheckbox() {
        JPanel panel = new JPanel();
        panel.setMinimumSize(new Dimension(300, 60));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        BoxLayout layout = new BoxLayout(panel, BoxLayout.PAGE_AXIS);
        panel.setLayout(layout);
        panel.add(Box.createHorizontalGlue());
        panel.add(new JSeparator());
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        allCheckbox = new JCheckBox(I18n.tr("Check/Uncheck all"));
        panel.add(allCheckbox);
        allCheckbox.setSelected(areAll(true));
        allCheckbox.addItemListener(e -> {
            JCheckBox cBox = (JCheckBox) e.getItemSelectable();
            checkAll(cBox.isSelected());
        });
        return panel;
    }

    private JComponent createSearchEnginesCheckboxPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("insets 2, wrap 4, gap 8"));
        List<SearchEngine> searchEngines = SearchEngine.getEngines();
        setupCheckboxes(searchEngines, panel);
        return panel;
    }

    private void setupCheckboxes(List<SearchEngine> searchEngines, JPanel parent) {
        for (SearchEngine se : searchEngines) {
            JCheckBox cBox = new JCheckBox(se.getName());
            searchEngineCheckboxes.add(cBox);
            cBox.setSelected(se.isEnabled());
            cBox.setEnabled(true);
            parent.add(cBox);
            cBoxes.put(cBox, se.getEnabledSetting());
            cBox.addItemListener(searchEnginesCheckboxListener);
        }
    }

    private boolean areAll(boolean selected) {
        for (JCheckBox cBox : searchEngineCheckboxes) {
            if (selected && !cBox.isSelected() || !selected && cBox.isSelected()) {
                return false;
            }
        }
        return true;
    }

    private void checkAll(boolean checked) {
        searchEnginesCheckboxListener.enable(false);
        for (JCheckBox cBox : searchEngineCheckboxes) {
            cBox.setSelected(checked);
            cBoxes.get(cBox).setValue(cBox.isSelected());
        }
        searchEnginesCheckboxListener.enable(true);
        // Check the first if all unchecked.
        if (!checked) {
            searchEngineCheckboxes.get(0).setSelected(true);
        }
    }

    private void autoSelectAllCheckbox(boolean allSelected) {
        final ItemListener[] itemListeners = allCheckbox.getItemListeners();
        for (ItemListener l : itemListeners) {
            allCheckbox.removeItemListener(l);
        }
        allCheckbox.setSelected(allSelected);
        for (ItemListener l : itemListeners) {
            allCheckbox.addItemListener(l);
        }
    }

    private class SearchEngineCheckboxListener implements ItemListener {
        final Map<JCheckBox, BooleanSetting> cBoxes;
        private boolean enabled;

        SearchEngineCheckboxListener(final Map<JCheckBox, BooleanSetting> cBoxes) {
            this.cBoxes = cBoxes;
            this.enabled = true;
        }

        void enable(boolean e) {
            enabled = e;
        }

        public void itemStateChanged(ItemEvent e) {
            if (enabled) {
                JCheckBox cBox = (JCheckBox) e.getItemSelectable();
                if (areAll(false)) {
                    cBox.setSelected(true);
                    return;
                }
                cBoxes.get(cBox).setValue(cBox.isSelected());
            }
            autoSelectAllCheckbox(areAll(true));
        }
    }
}