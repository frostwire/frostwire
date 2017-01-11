/*
 * Copyright (C) 2010-2011 Mike Novak <michael.novakjr@gmail.com>
 * Copyright (C) 2011-2017, FrostWire(R). All rights reserved.
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
package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.frostwire.android.R;

/**
 * see {@link NumberPickerPreferenceDialogFragment} for explanation
 *
 * @author grzesiekrzaca
 */
public class NumberPickerPreference2 extends DialogPreference {

    private int startRange;
    private int endRange;
    private int defaultValue;

    public NumberPickerPreference2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (attrs == null) {
            return;
        }
        setIcon(null);
        setDialogLayoutResource(R.layout.dialog_preference_number_picker2);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.numberpicker);
        startRange = arr.getInteger(R.styleable.numberpicker_picker_startRange, 0);
        endRange = arr.getInteger(R.styleable.numberpicker_picker_endRange, 200);
        defaultValue = arr.getInteger(R.styleable.numberpicker_picker_defaultValue, 0);
        arr.recycle();
    }

    public NumberPickerPreference2(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public NumberPickerPreference2(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        //fixing code error that doesn't hide properly the layout that holds the icon
        //can't find parent of icon directly for some reason so we find the icon and set it's parent visibility
        final ImageView imageView = (ImageView) holder.findViewById(android.R.id.icon);
        if (imageView != null) {
            ViewGroup parent = (LinearLayout) imageView.getParent();
            if (parent != null) {
                parent.setVisibility(getIcon() != null ? View.VISIBLE : View.GONE);
            }
        }
    }

    protected void saveValue(long val) {
        persistLong(val);
        notifyChanged();
    }

    public int getStartRange() {
        return startRange;
    }

    public int getEndRange() {
        return endRange;
    }

    public int getDefaultValue() {
        return defaultValue;
    }
}