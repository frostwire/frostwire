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

import javax.swing.JPanel;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public interface SlideshowPanel {

    public void setListener(SlideshowListener listener);

    public int getCurrentSlideIndex();

    public void switchToSlide(int slideIndex);

    public int getNumSlides();

    public void setVisible(boolean visible);

    public void setupContainerAndControls(JPanel container, boolean useControls);

    public interface SlideshowListener {
        public void onSlideChanged();
    }
}