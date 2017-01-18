/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Grzesiek Rząca (grzesiekrzaca)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.frostwire.android.R;

/**
 * Used to connect preference UI state with it's background
 * If not used in a checkbox preference will work as normal LinearLayout
 */
public class PreferenceSlaveLinearLayout extends LinearLayout {
    private CheckBox master;

    private Drawable checkedColor;
    private Drawable unCheckedColor;

    public PreferenceSlaveLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        checkedColor = context.getResources().getDrawable(R.color.selected_search_background);
        unCheckedColor = context.getResources().getDrawable(R.color.basic_white);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        master = (CheckBox) this.findViewById(android.R.id.checkbox);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (master != null) {
            setBackground(master.isChecked() ? checkedColor : unCheckedColor);
        }
        super.onDraw(canvas);
    }

}
