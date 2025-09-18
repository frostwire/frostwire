/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.I18n;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author gubatron
 */
class LabeledRangeSlider extends JPanel {
    private final RangeSlider slider;
    private final JLabel titleLabel;
    private final JLabel minLabel;
    private final JLabel maxLabel;

    /**
     * @param title          - No need to pass through I18n.tr()
     * @param defaultMaxText - optional. No need to pass through I18n.tr()
     */
    LabeledRangeSlider(String title, String defaultMaxText, int minValue, int maxValue) {
        slider = new RangeSlider();
        slider.setValue(minValue);
        slider.setUpperValue(maxValue);
        titleLabel = new JLabel(I18n.tr(title));
        minLabel = new JLabel(String.valueOf(minValue));
        if (defaultMaxText == null) {
            maxLabel = new JLabel(I18n.tr("Max"));
        } else {
            maxLabel = new JLabel(I18n.tr(defaultMaxText));
        }
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = GridBagConstraints.REMAINDER;
        //add the title
        add(titleLabel, c);
        //add the slider
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        add(slider, c);
        //add the min and max labels
        c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.LINE_START;
        add(minLabel, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createGlue(), c);
        c = new GridBagConstraints();
        c.gridx = 2;
        c.anchor = GridBagConstraints.LINE_START;
        add(maxLabel, c);
    }

    public void addChangeListener(ChangeListener listener) {
        slider.addChangeListener(listener);
    }

    int getLowerValue() {
        return slider.getLowerValue();
    }

    /**
     * Sets the lower value in the range.
     */
    void setLowerValue(@SuppressWarnings("SameParameterValue") int value) {
        slider.setLowerValue(value);
    }

    int getUpperValue() {
        return slider.getUpperValue();
    }

    void setUpperValue(@SuppressWarnings("SameParameterValue") int value) {
        slider.setUpperValue(value);
    }

    public void setMinimum(int min) {
        slider.setMinimum(min);
    }

    public void setMaximum(int max) {
        slider.setMaximum(max);
    }

    JLabel getMinimumValueLabel() {
        return minLabel;
    }

    JLabel getMaximumValueLabel() {
        return maxLabel;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        slider.setEnabled(enabled);
    }
}
