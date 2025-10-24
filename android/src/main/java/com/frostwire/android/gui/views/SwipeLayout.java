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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * @author gubatron
 * @author aldenml
 */
public class SwipeLayout extends LinearLayout {

    private static final float DELTA_THRESHOLD = 100;
    private static final float VELOCITY_THRESHOLD = 100;

    private static final float LEFT_MARGIN = 70;

    private GestureDetector detector;
    private OnSwipeListener listener;

    public SwipeLayout(Context context) {
        this(context, null);
    }

    public SwipeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (detector == null) {
            detector = new GestureDetector(getContext(), new SwipeGestureAdapter() {
                @Override
                public void onSwipeLeft() {
                    if (listener != null) {
                        listener.onSwipeLeft();
                    }
                }

                @Override
                public void onSwipeRight() {
                    if (listener != null) {
                        listener.onSwipeRight();
                    }
                }
            });
        }

        try {
            return (detector != null && detector.onTouchEvent(ev)) || super.dispatchTouchEvent(ev);
        } catch (Throwable t) {
            return false;
        }
    }

    public OnSwipeListener getOnSwipeListener() {
        return listener;
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.listener = listener;
    }

    public static class SwipeGestureAdapter extends SimpleOnGestureListener {

        private MotionEvent lastOnDownEvent = null;

        public SwipeGestureAdapter() {
        }

        @Override
        public boolean onDown(MotionEvent e) {
            lastOnDownEvent = e;
            return false;
        }

        @Override
        public final boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null) {
                e1 = lastOnDownEvent;
            }
            if (e1 == null || e2 == null) {
                return false;
            }

            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();

            if (Math.abs(dx) > Math.abs(dy)) {
                if (Math.abs(dx) > DELTA_THRESHOLD && Math.abs(velocityX) > VELOCITY_THRESHOLD) {
                    if (dx > 0) {
                        if (e1.getX() >= LEFT_MARGIN) {
                            onSwipeRight();
                        }
                    } else {
                        onSwipeLeft();
                    }
                }
            }

            return false;
        }

        public void onSwipeLeft() {
        }

        public void onSwipeRight() {
        }
    }

    public interface OnSwipeListener {

        void onSwipeLeft();

        void onSwipeRight();
    }
}
