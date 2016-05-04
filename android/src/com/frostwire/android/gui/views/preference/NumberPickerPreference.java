/*
 * Copyright (C) 2010-2011 Mike Novak <michael.novakjr@gmail.com>
 * Copyright (C) 2011-2016, FrostWire(R). All rights reserved.
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;

import java.util.prefs.Preferences;

public class NumberPickerPreference extends DialogPreference {
    private NumberPicker mPicker;
    private int mStartRange;
    private int mEndRange;
    private int mDefault;

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (attrs == null) {
            return;
        }

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.numberpicker);
        mStartRange = arr.getInteger(R.styleable.numberpicker_picker_startRange, 0);
        mEndRange = arr.getInteger(R.styleable.numberpicker_picker_endRange, 200);
        mDefault = arr.getInteger(R.styleable.numberpicker_picker_defaultValue, 0);

        arr.recycle();

        setDialogLayoutResource(R.layout.dialog_preference_number_picker);

        //TODO: set the title on the custom dialogTitle TextView below
        setDialogTitle(null);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public NumberPickerPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mPicker = (NumberPicker) view.findViewById(R.id.dialog_preference_number_picker);
        setRange(mStartRange, mEndRange);
        mPicker.setValue((int) getValue());

        //TODO: set correct title
        TextView dialogTitle = (TextView) view.findViewById(R.id.dialog_preference_number_title);

        //TODO: Connect the positive and negative custom buttons
        Button noButton = (Button) view.findViewById(R.id.dialog_preference_number_button_no);
        noButton.setText(R.string.cancel);

        Button yesButton = (Button) view.findViewById(R.id.dialog_preference_number_button_yes);
        yesButton.setText(android.R.string.ok);
//        yesButton.setOnClickListener(new PositiveButtonOnClickListener(this));

    }


//    public void onClick(DialogInterface dialog, int which) {
//        switch (which) {
//        case DialogInterface.BUTTON_POSITIVE:
//            saveValue(mPicker.getValue());
//            final OnPreferenceChangeListener onPreferenceChangeListener = getOnPreferenceChangeListener();
//            if (onPreferenceChangeListener != null) {
//                try {
//                    onPreferenceChangeListener.onPreferenceChange(this, mPicker.getValue());
//                } catch (Throwable t) {
//                    t.printStackTrace();
//                }
//            }
//            break;
//        default:
//            break;
//        }
//    }

    public void setRange(int start, int end) {
        mPicker.setMinValue(start);
        mPicker.setMaxValue(end);
    }

    private void saveValue(long val) {
        getEditor().putLong(getKey(), val).commit();
        notifyChanged();
    }

    private long getValue() {
        return getSharedPreferences().getLong(getKey(), mDefault);
    }


    //TODO: Connect the positive and negative custom buttons
//    private class PositiveButtonOnClickListener implements View.OnClickListener {
//        private final NumberPickerPreference numberPickerPreference;
//
//        public PositiveButtonOnClickListener(NumberPickerPreference numberPickerPreference) {
//            this.numberPickerPreference = numberPickerPreference;
//        }
//
//        @Override
//        public void onClick(View view) {
//            saveValue(mPicker.getValue());
//            final OnPreferenceChangeListener onPreferenceChangeListener = getOnPreferenceChangeListener();
//            if (onPreferenceChangeListener != null) {
//                try {
//                    onPreferenceChangeListener.onPreferenceChange(this, mPicker.getValue());
//                } catch (Throwable t) {
//                    t.printStackTrace();
//                }
//            }
//        }
//
//    }
}


//    private class NegativeButtonOnClickListener implements View.OnClickListener {
//        public NegativeButtonOnClickListener(NumberPickerPreference numberPickerPreference) {
//        }
//
//        @Override
//        public void onClick(View view) {
//
//        }
//    }
//}
