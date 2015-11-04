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

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;

/**
 * This class is a workaround to an old android issue #6191:
 * http://code.google.com/p/android/issues/detail?id=6191 
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public class ViewFlipper extends android.widget.ViewFlipper {

    public ViewFlipper(Context context) {
        super(context);
    }

    public ViewFlipper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow();
        } catch (IllegalArgumentException e) {
            // ignoring IllegalArgumentException
            // calling this to trigger updateRunning()
            stopFlipping();
        }
    }
}