/*
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

import android.app.DialogFragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v14.preference.PreferenceDialogFragment;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import com.frostwire.android.R;

/**
 * @author grzesiekrzaca
 *
 * Support version of a custom dialog preference
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

    /**
     * Actual dialog used to interact with the preference
     */
    public static class NumberPickerPreferenceDialog extends PreferenceDialogFragment {


        public static final String START_RANGE = "startRange";
        public static final String END_RANGE = "endRange";
        public static final String DEFAULT_VALUE = "defaultValue";

        private int mStartRange;
        private int mEndRange;
        private int mDefault;
        private NumberPicker mPicker;

        public NumberPickerPreferenceDialog() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mStartRange = getArguments().getInt(START_RANGE);
            mEndRange = getArguments().getInt(END_RANGE);
            mDefault = getArguments().getInt(DEFAULT_VALUE);
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);
            mPicker = (NumberPicker) view.findViewById(R.id.dialog_preference_number_picker);
            setRange(mStartRange, mEndRange);
            mPicker.setValue((int) getPreference().getSharedPreferences().getLong(getKey(), mDefault));
        }

        private String getKey() {
            return getPreference().getKey();
        }

        private void setRange(int start, int end) {
            mPicker.setMinValue(start);
            mPicker.setMaxValue(end);
        }


        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                ((NumberPickerPreference2) getPreference()).saveValue(mPicker.getValue());
                final Preference.OnPreferenceChangeListener onPreferenceChangeListener = getPreference().getOnPreferenceChangeListener();
                if (onPreferenceChangeListener != null) {
                    try {
                        onPreferenceChangeListener.onPreferenceChange(getPreference(), mPicker.getValue());
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }

        public static DialogFragment newInstance(Preference preference) {
            NumberPickerPreferenceDialog fragment = new NumberPickerPreferenceDialog();
            Bundle bundle = new Bundle(4);
            bundle.putString(ARG_KEY, preference.getKey());
            bundle.putInt(START_RANGE, ((NumberPickerPreference2) preference).getStartRange());
            bundle.putInt(END_RANGE, ((NumberPickerPreference2) preference).getEndRange());
            bundle.putInt(DEFAULT_VALUE, ((NumberPickerPreference2) preference).getDefaultValue());
            fragment.setArguments(bundle);
            return fragment;
        }
    }
}