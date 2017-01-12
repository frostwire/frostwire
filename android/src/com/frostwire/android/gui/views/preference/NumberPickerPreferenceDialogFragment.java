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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.frostwire.android.R;

/**
 * Preference classes in Android are a dumpster-fire.
 * Compatibility versions are even worse. To write a custom preference one needs two classes:
 * One that holds the preference, the other for the fragment that will show it.
 * And to Show that fragment we need to create a special listener. See {@link com.frostwire.android.gui.activities.SettingsActivity2.Torrent}
 * Screw encapsulation and readability of the code.
 */

public class NumberPickerPreferenceDialogFragment extends PreferenceDialogFragmentCompat{


    private int mStartRange;
    private int mEndRange;
    private int mDefault;
    private NumberPicker mPicker;
    private TextView mCustomTitleView;

    public NumberPickerPreferenceDialogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStartRange = getArguments().getInt("startRange");
        mEndRange = getArguments().getInt("endRange");
        mDefault = getArguments().getInt("defaultText");
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        final ViewGroup parent = (ViewGroup) mCustomTitleView.getParent();
        parent.removeView(mCustomTitleView);
        builder.setCustomTitle(mCustomTitleView);

        builder.setPositiveButton(null, null);
        builder.setNegativeButton(null, null);
    }

    @Override
    protected View onCreateDialogView(Context context) {
        View view = super.onCreateDialogView(context);
        mPicker = (NumberPicker) view.findViewById(R.id.dialog_preference_number_picker);
        setRange(mStartRange, mEndRange);
        mPicker.setValue((int) getPreference().getSharedPreferences().getLong(getKey(), mDefault));

        mCustomTitleView = (TextView) view.findViewById(R.id.dialog_preference_number_title);
        mCustomTitleView.setText(getPreference().getDialogTitle());

        // Custom buttons on our layout.
        Button yesButton = (Button) view.findViewById(R.id.dialog_preference_number_button_yes);
        yesButton.setText(android.R.string.ok);
        yesButton.setOnClickListener(new NumberPickerPreferenceDialogFragment.PositiveButtonOnClickListener(this));

        Button noButton = (Button) view.findViewById(R.id.dialog_preference_number_button_no);
        noButton.setText(R.string.cancel);
        noButton.setOnClickListener(new NumberPickerPreferenceDialogFragment.NegativeButtonOnClickListener(this));
        return view;
    }

    private String getKey(){
        return getPreference().getKey();
    }

    private void setRange(int start, int end) {
        mPicker.setMinValue(start);
        mPicker.setMaxValue(end);
    }


    @Override
    public void onDialogClosed(boolean positiveResult) {

    }

    public static DialogFragment newInstance(Preference preference, Context context) {
        TypedArray arr = context.obtainStyledAttributes(R.styleable.numberpicker);
        NumberPickerPreferenceDialogFragment fragment = new NumberPickerPreferenceDialogFragment();
        int startRange = arr.getInteger(R.styleable.numberpicker_picker_startRange, 0);
        int endRange = arr.getInteger(R.styleable.numberpicker_picker_endRange, 200);
        int defaultText = arr.getInteger(R.styleable.numberpicker_picker_defaultValue, 0);
        arr.recycle();
        Bundle bundle = new Bundle(4);
        bundle.putString(ARG_KEY, preference.getKey());
        bundle.putInt("startRange",startRange);
        bundle.putInt("endRange",endRange);
        bundle.putInt("defaultText",defaultText);
        fragment.setArguments(bundle);
        return fragment;
    }


    private class PositiveButtonOnClickListener implements View.OnClickListener {
        private final NumberPickerPreferenceDialogFragment dlgPreference;

        PositiveButtonOnClickListener(NumberPickerPreferenceDialogFragment dlgPreference) {
            this.dlgPreference = dlgPreference;
        }

        @Override
        public void onClick(View view) {
            ((NumberPickerPreference2) getPreference()).saveValue(mPicker.getValue());
            final Preference.OnPreferenceChangeListener onPreferenceChangeListener = getPreference().getOnPreferenceChangeListener();
            if (onPreferenceChangeListener != null) {
                try {
                    onPreferenceChangeListener.onPreferenceChange(getPreference(), mPicker.getValue());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            dlgPreference.getDialog().dismiss();
        }
    }

    private class NegativeButtonOnClickListener implements View.OnClickListener {
        private final NumberPickerPreferenceDialogFragment dlgPreference;

        public NegativeButtonOnClickListener(NumberPickerPreferenceDialogFragment dlgPreference) {
            this.dlgPreference = dlgPreference;
        }

        @Override
        public void onClick(View v) {
            dlgPreference.getDialog().dismiss();
        }
    }
}
