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

package com.frostwire.gui.components.slides;

import com.frostwire.gui.components.slides.SlideshowPanel.SlideshowListener;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
class SlideshowPanelControls extends JPanel implements SlideshowListener {
    private final SlideshowPanel _thePanel;
    private List<JRadioButton> _buttons;
    private ItemListener _selectionAdapter;

    public SlideshowPanelControls(SlideshowPanel panel) {
        _thePanel = panel;
        _thePanel.setListener(this);
        buildButtons();
        autoSelectCurrentSlideButton();
        buildItemListener();
        attachListeners();
    }

    public void autoSelectCurrentSlideButton() {
        int currentSlideIndex = _thePanel.getCurrentSlideIndex();
        if (currentSlideIndex != -1) {
            _buttons.get(currentSlideIndex).setSelected(true);
        } else {
            _buttons.get(0).setSelected(true);
        }
    }

    private void buildItemListener() {
        _selectionAdapter = e -> {
            if (((JRadioButton) e.getItemSelectable()).isSelected()) {
                onRadioButtonClicked(e);
            }
        };
    }

    protected void onRadioButtonClicked(ItemEvent e) {
        int selectedIndex = _buttons.indexOf(e.getSource());
        _thePanel.switchToSlide(selectedIndex);
    }

    private void buildButtons() {
        int numSlides = _thePanel.getNumSlides();
        ButtonGroup _buttonGroup = new ButtonGroup();
        _buttons = new ArrayList<>(numSlides);
        for (int i = 0; i < numSlides; i++) {
            JRadioButton radio = new JRadioButton();
            //add to the list
            _buttons.add(radio);
            //add to the button group
            _buttonGroup.add(radio);
            //add to the panel
            add(radio);
        }
    }

    private void attachListeners() {
        for (JRadioButton button : _buttons) {
            button.addItemListener(_selectionAdapter);
        }
    }

    @Override
    public void onSlideChanged() {
        int currentSlideIndex = _thePanel.getCurrentSlideIndex();
        JRadioButton button = _buttons.get(currentSlideIndex);
        ItemListener[] itemListeners = button.getItemListeners();
        for (ItemListener listener : itemListeners) {
            button.removeItemListener(listener);
        }
        button.setSelected(true);
        for (ItemListener listener : itemListeners) {
            button.addItemListener(listener);
        }
    }
}
