/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2012, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.mplayer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;

class ProgressSlider extends JPanel {
    private static final long serialVersionUID = 8000075870624583383L;
    private final JSlider progressSlider;
    private final JLabel remainingTime;
    private final JLabel elapsedTime;
    private float totalTime = 0;
    private float currentTime = 0;
    private boolean mousePressed = false;
    private final LinkedList<ProgressSliderListener> listeners = new LinkedList<>();

    ProgressSlider() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setSize(new Dimension(411, 17));
        elapsedTime = new JLabel("--:--");
        Font font = new Font(elapsedTime.getFont().getFontName(), Font.BOLD, 14);
        elapsedTime.setFont(font);
        elapsedTime.setForeground(Color.white);
        add(elapsedTime, BorderLayout.WEST);
        progressSlider = new JSlider();
        progressSlider.setOpaque(false);
        progressSlider.setFocusable(false);
        progressSlider.addChangeListener(e -> {
            if (mousePressed) {
                float seconds = (float) (progressSlider.getValue() / 100.0 * totalTime);
                for (ProgressSliderListener l : listeners) {
                    l.onProgressSliderTimeValueChange(seconds);
                }
            }
        });
        progressSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mousePressed = true;
                for (ProgressSliderListener l : listeners) {
                    l.onProgressSliderMouseDown();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mousePressed = false;
                for (ProgressSliderListener l : listeners) {
                    l.onProgressSliderMouseUp();
                }
            }
        });
        add(progressSlider, BorderLayout.CENTER);
        remainingTime = new JLabel("--:--");
        remainingTime.setFont(font);
        remainingTime.setForeground(Color.white);
        add(remainingTime, BorderLayout.EAST);
    }

    void addProgressSliderListener(ProgressSliderListener listener) {
        listeners.add(listener);
    }

    void setTotalTime(float seconds) {
        if (seconds != totalTime) {
            totalTime = seconds;
            currentTime = 0;
            updateUIControls();
        }
    }

    void setCurrentTime(float seconds) {
        if (seconds != currentTime) {
            currentTime = seconds;
            updateUIControls();
        }
    }

    private void updateUIControls() {
        elapsedTime.setText(TimeUtils.getTimeFormatedString((int) currentTime));
        remainingTime.setText(TimeUtils.getTimeFormatedString((int) (totalTime - currentTime)));
        int progressValue = (int) (currentTime / totalTime * 100.0);
        progressSlider.setValue(Math.max(0, Math.min(100, progressValue)));
    }
}
