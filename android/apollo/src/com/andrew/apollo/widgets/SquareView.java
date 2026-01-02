/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * A custom {@link ViewGroup} used to make it's children into perfect squares.
 * Useful when dealing with grid images and especially album art.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class SquareView extends ViewGroup {

    /**
     * @param context The {@link Context} to use
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public SquareView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final View mChildren = getChildAt(0);
            if (mChildren == null) {
                setMeasuredDimension(0, 0);
                return;
            }
            
            try {
                mChildren.measure(widthMeasureSpec, widthMeasureSpec);
                final int mWidth = resolveSize(mChildren.getMeasuredWidth(), widthMeasureSpec);
                mChildren.measure(mWidth, mWidth);
                setMeasuredDimension(mWidth, mWidth);
            } catch (Exception e) {
                e.printStackTrace();
                setMeasuredDimension(0, 0);
            }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(final boolean changed, final int l, final int u, final int r,
            final int d) {
        getChildAt(0).layout(0, 0, r - l, d - u);
    }

    /**
     * we're not calling super.requestLayout() on purpose
     */
    @SuppressLint("MissingSuperCall")
    @Override
    public void requestLayout() {
        forceLayout();
    }
}
