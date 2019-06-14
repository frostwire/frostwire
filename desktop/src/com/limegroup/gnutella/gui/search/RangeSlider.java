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

import com.frostwire.gui.theme.SkinRangeSliderUI;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class RangeSlider extends JSlider {
    private int thumbNum;
    private BoundedRangeModel[] sliderModels;

    public RangeSlider() {
        createThumbs(2);
        updateUI();
    }

    public int getLowerValue() {
        return getValueAt(0);
    }

    public void setLowerValue(int newValue) {
        setValueAt(newValue, 0);
    }

    public int getUpperValue() {
        return getValueAt(1);
    }

    public void setUpperValue(int newValue) {
        setValueAt(newValue, 1);
    }

    public int getThumbNum() {
        return thumbNum;
    }

    public int getValueAt(int index) {
        return getModelAt(index).getValue();
    }

    public void setValueAt(int newValue, int index) {
        if (index == 0) {
            newValue = Math.min(getValueAt(1) - 1, newValue);
        }
        if (index == 1) {
            newValue = Math.max(getValueAt(0) + 1, newValue);
        }
        getModelAt(index).setValue(newValue);
        getModelAt(index).setRangeProperties(newValue, getExtent(), getMinimum(), getMaximum(), getValueIsAdjusting());
    }

    @Override
    public int getMinimum() {
        return getModelAt(0).getMinimum();
    }

    @Override
    public void setMinimum(int minimum) {
        int oldMin = getModelAt(0).getMinimum();
        for (BoundedRangeModel model : sliderModels) {
            model.setMinimum(minimum);
        }
        firePropertyChange("minimum", Integer.valueOf(oldMin), Integer.valueOf(minimum));
    }

    @Override
    public int getMaximum() {
        return getModelAt(0).getMaximum();
    }

    @Override
    public void setMaximum(int maximum) {
        int oldMax = getModelAt(0).getMaximum();
        for (BoundedRangeModel model : sliderModels) {
            model.setMaximum(maximum);
        }
        firePropertyChange("maximum", Integer.valueOf(oldMax), Integer.valueOf(maximum));
    }

    @Override
    public int getValue() {
        return 0; // returns a dummy value
    }

    @Override
    public void setValue(int n) {
        // ignore
    }

    public BoundedRangeModel getModelAt(int index) {
        return sliderModels[index];
    }

    @Override
    public void updateUI() {
        super.setUI(new SkinRangeSliderUI(this));
    }

    private void createThumbs(int n) {
        thumbNum = n;
        sliderModels = new BoundedRangeModel[n];
        for (int i = 0; i < n; i++) {
            sliderModels[i] = new DefaultBoundedRangeModel(50, 0, 0, 100);
        }
        sliderModels[0].setValue(0);
        sliderModels[1].setValue(100);
    }
}
