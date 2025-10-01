/*
 *     Created by Angel Leon (@gubatron)
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
import androidx.drawerlayout.widget.DrawerLayout;

import com.frostwire.util.Logger;

/**
 * A DrawerLayout that safely falls back to the provided measure specs
 * even when not measured with MeasureSpec.EXACTLY, preventing the
 * IllegalStateException thrown by DrawerLayout.onMeasure().
 */
public class SafeDrawerLayout extends DrawerLayout {
    private final Logger LOG = Logger.getLogger(SafeDrawerLayout.class);

    public SafeDrawerLayout(Context context) {
        super(context);
    }

    public SafeDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SafeDrawerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = android.view.View.MeasureSpec.getSize(widthMeasureSpec);
        int height = android.view.View.MeasureSpec.getSize(heightMeasureSpec);
        boolean measureSuccess = false;

        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            measureSuccess = true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOG.error("DrawerLayout.onMeasure() failed; falling back to imposed measure specs", e);
        }

        // If super.onMeasure() didn't successfully set the dimensions,
        // we need to explicitly call setMeasuredDimension()
        if (!measureSuccess) {
            setMeasuredDimension(width, height);
        }
    }
}
