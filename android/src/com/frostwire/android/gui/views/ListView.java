/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;

/**
 * @author gubatron
 * @author aldenml
 */
public class ListView extends android.widget.ListView {

    private static final int MAX_Y_OVERSCROLL_DISTANCE = 100;
    private final int mMaxYOverscrollDistance;

    private OverScrollListener overScrollListener = null;

    public ListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mMaxYOverscrollDistance = calculateMaxOverscrollScreenDistance(context);
    }

    public ListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxYOverscrollDistance = calculateMaxOverscrollScreenDistance(context);
    }

    public ListView(Context context) {
        super(context);
        mMaxYOverscrollDistance = calculateMaxOverscrollScreenDistance(context);
    }

    public void setOverScrollListener(OverScrollListener listener) {
        overScrollListener = listener;
    }

    private static int calculateMaxOverscrollScreenDistance(Context context) {
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final float density = metrics.density;
        return (int) (density * MAX_Y_OVERSCROLL_DISTANCE);
    }

    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, mMaxYOverscrollDistance, isTouchEvent);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        if (overScrollListener != null) {
            try {
                overScrollListener.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
