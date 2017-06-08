/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
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
