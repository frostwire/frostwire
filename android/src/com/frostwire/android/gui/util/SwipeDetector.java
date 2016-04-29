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
    private long lastSwipeTimestamp = System.currentTimeMillis();

    /**
     * @param swipeListener - The object that will act on swipe left or right.
     * @param leftMargin - Ignore events to the left of this X coordinate.
     */
    public SwipeDetector(SwipeListener swipeListener,
                         float leftMargin) {
        this.swipeListener = swipeListener;
        this.leftMargin = leftMargin;
    }

    @SuppressWarnings("unused")
    public SwipeDetector(SwipeListener swipeListener) {
        this(swipeListener, 0);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if ((System.currentTimeMillis() - lastSwipeTimestamp) < 450) {
            return false;
        }

        if (leftMargin > 0 &&
            event.getAction() == MotionEvent.ACTION_DOWN &&
            event.getX() < leftMargin) {
            return false;
        }

        int historySize = event.getHistorySize();
        if (event.getAction() == MotionEvent.ACTION_MOVE && historySize > 1) {
            float x1 = event.getHistoricalX(0,0);
            float y1 = event.getHistoricalY(0,0);
            float x2 = event.getHistoricalX(0,historySize-1);
            float y2 = event.getHistoricalY(0,historySize-1);
            float deltaX = x2-x1;
            float deltaY = Math.abs(y2-y1);
            if (deltaY > Math.abs(deltaX)) {
                return false;
            }
            if (Math.abs(deltaX) <= 10) {
                return false;
            }
            if (deltaX > 0) {
                swipeListener.onSwipeRight();
            } else {
                swipeListener.onSwipeLeft();
            }
            lastSwipeTimestamp = System.currentTimeMillis();
        }
        return false;
    }
}
