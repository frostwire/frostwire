/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.search;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import com.limegroup.gnutella.gui.I18n;

/**
 * 
 * @author gubatron
 *
 */
public class LabeledRangeSlider extends JPanel {

    private final RangeSlider slider;

    private final JLabel titleLabel;
    private final JLabel minLabel;
    private final JLabel maxLabel;

    /**
     * 
     * @param title - No need to pass through I18n.tr()
     * @param defaultMaxText - optional. No need to pass through I18n.tr()
     * @param minValue
     * @param maxValue
     */
    public LabeledRangeSlider(String title, String defaultMaxText, int minValue, int maxValue) {
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

    /**
     * Sets the lower value in the range.
     */
    public void setLowerValue(int value) {
        slider.setLowerValue(value);
    }

    public int getLowerValue() {
        return slider.getLowerValue();
    }

    public int getUpperValue() {
        return slider.getUpperValue();
    }

    public void setUpperValue(int value) {
        slider.setUpperValue(value);
    }

    public void setMinimum(int min) {
        slider.setMinimum(min);
    }

    public void setMaximum(int max) {
        slider.setMaximum(max);
    }

    public JLabel getMinimumValueLabel() {
        return minLabel;
    }

    public JLabel getMaximumValueLabel() {
        return maxLabel;
    }

    public JLabel getTitleLabel() {
        return titleLabel;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        slider.setEnabled(enabled);
    }
}
