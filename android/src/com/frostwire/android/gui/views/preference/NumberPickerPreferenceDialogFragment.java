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
import android.os.Bundle;
import android.support.v14.preference.PreferenceDialogFragment;
import android.support.v7.preference.Preference;
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
 */

public class NumberPickerPreferenceDialogFragment extends PreferenceDialogFragment {


    public static final String START_RANGE = "startRange";
    public static final String END_RANGE = "endRange";
    public static final String DEFAULT_VALUE = "defaultValue";

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
        mStartRange = getArguments().getInt(START_RANGE);
        mEndRange = getArguments().getInt(END_RANGE);
        mDefault = getArguments().getInt(DEFAULT_VALUE);
    }

    @Override
    protected void onPrepareDialogBuilder(android.app.AlertDialog.Builder builder) {
        final ViewGroup parent = (ViewGroup) mCustomTitleView.getParent();
        parent.removeView(mCustomTitleView);
        builder.setCustomTitle(mCustomTitleView);

        builder.setPositiveButton(null, null);
        builder.setNegativeButton(null, null);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mPicker = (NumberPicker) view.findViewById(R.id.dialog_preference_number_picker);
        setRange(mStartRange, mEndRange);
        mPicker.setValue((int) getPreference().getSharedPreferences().getLong(getKey(), mDefault));

        mCustomTitleView = (TextView) view.findViewById(R.id.dialog_preference_number_title);
        mCustomTitleView.setText(getPreference().getDialogTitle());

        // Custom buttons on our layout.
        Button yesButton = (Button) view.findViewById(R.id.dialog_preference_number_button_yes);
        yesButton.setText(android.R.string.ok);
        yesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((NumberPickerPreference2) getPreference()).saveValue(mPicker.getValue());
                final Preference.OnPreferenceChangeListener onPreferenceChangeListener = getPreference().getOnPreferenceChangeListener();
                if (onPreferenceChangeListener != null) {
                    try {
                        onPreferenceChangeListener.onPreferenceChange(getPreference(), mPicker.getValue());
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
                NumberPickerPreferenceDialogFragment.this.dismiss();
            }
        });

        Button noButton = (Button) view.findViewById(R.id.dialog_preference_number_button_no);
        noButton.setText(R.string.cancel);
        noButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NumberPickerPreferenceDialogFragment.this.dismiss();
            }
        });
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
        //do nothing (don't save the preference - clcik outside of dialog or back button)
    }

    public static DialogFragment newInstance(Preference preference) {
        NumberPickerPreferenceDialogFragment fragment = new NumberPickerPreferenceDialogFragment();
        Bundle bundle = new Bundle(4);
        bundle.putString(ARG_KEY, preference.getKey());
        bundle.putInt(START_RANGE, ((NumberPickerPreference2) preference).getStartRange());
        bundle.putInt(END_RANGE, ((NumberPickerPreference2) preference).getEndRange());
        bundle.putInt(DEFAULT_VALUE, ((NumberPickerPreference2) preference).getDefaultValue());
        fragment.setArguments(bundle);
        return fragment;
    }

}
