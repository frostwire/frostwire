/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.util;

import android.view.MotionEvent;
import android.view.View;
import com.frostwire.logging.Logger;

import java.util.LinkedList;
import java.util.List;

/**
 * Created on 4/26/16.
 *
 * @author gubatron
 * @author aldenml
 */
public final class SwipeDetector implements
        View.OnTouchListener {
    @SuppressWarnings("unused")
    private static Logger LOG = Logger.getLogger(SwipeDetector.class);
    private final SwipeListener swipeListener;
    private final float leftMargin;
    private List<Float> xPositions = new LinkedList<>();
    private long lastTouch = System.currentTimeMillis();

    /**
     * @param swipeListener - The object that will act on swipe left or right.
     * @param leftMargin - Ignore events to the left of this X coordinate.
     */
    public SwipeDetector(SwipeListener swipeListener,
                         float leftMargin) {
        this.swipeListener = swipeListener;
        this.leftMargin = leftMargin;
    }

    public SwipeDetector(SwipeListener swipeListener) {
        this(swipeListener, 0);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (leftMargin > 0 && event.getX() < leftMargin) {
            return false;
        }
        long timeSinceLastEvent = System.currentTimeMillis() - lastTouch;
        lastTouch = System.currentTimeMillis();
        if (timeSinceLastEvent > 600) {
            xPositions.clear();
        }
        xPositions.add(event.getX());
        final int lastIndex = xPositions.size() - 1;
        if (lastIndex > 4) {
            final float xFirst =  xPositions.get(0);
            final float xLast = xPositions.get(lastIndex);
            xPositions.clear();
            float deltaX = xLast - xFirst;
            int MIN_DISPLACEMENT = 100;
            if (Math.abs(deltaX) > MIN_DISPLACEMENT) {
                if (deltaX > 0) {
                    swipeListener.onSwipeRight();
                } else {
                    swipeListener.onSwipeLeft();
                }
                return true;
            }
        }
        return false;
    }
}
