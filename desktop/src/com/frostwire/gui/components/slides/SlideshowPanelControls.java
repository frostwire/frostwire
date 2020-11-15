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
