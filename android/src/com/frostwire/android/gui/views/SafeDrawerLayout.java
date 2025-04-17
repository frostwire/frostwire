/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * A DrawerLayout that safely falls back to the provided measure specs
 * even when not measured with MeasureSpec.EXACTLY, preventing the
 * IllegalStateException thrown by DrawerLayout.onMeasure().
 */
public class SafeDrawerLayout extends DrawerLayout {

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
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (IllegalStateException e) {
            // Fallback to whatever size the parent imposed, even if not EXACTLY
            setMeasuredDimension(
                    MeasureSpec.getSize(widthMeasureSpec),
                    MeasureSpec.getSize(heightMeasureSpec)
            );
        }
    }
}
