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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v14.preference.PreferenceDialogFragment;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractPreferenceFragment.PreferenceDialogFragment;
import com.frostwire.util.StringUtils;

/**
 * Support version of a custom dialog preference
 *
 * @author grzesiekrzaca
 */
public final class NumberPickerPreference2 extends DialogPreference {

    private int startRange;
    private int endRange;
    private int defaultValue;
    private Integer unlimitedValue;
    private boolean hasUnlimitedValue;

    public NumberPickerPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_preference_number_picker2);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.numberpicker);
        startRange = arr.getInteger(R.styleable.numberpicker_picker_startRange, 0);
        endRange = arr.getInteger(R.styleable.numberpicker_picker_endRange, 200);
        defaultValue = arr.getInteger(R.styleable.numberpicker_picker_defaultValue, 0);
        hasUnlimitedValue = arr.getBoolean(R.styleable.numberpicker_picker_hasUnlimitedValue, false);
        unlimitedValue = arr.getInteger(R.styleable.numberpicker_picker_unlimitedValue, -1);

        arr.recycle();
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

    public boolean getHasUnlimitedValue() {
        return hasUnlimitedValue;
    }

    public Integer getUnlimitedValue() {
        return unlimitedValue;
    }

    /**
     * Actual dialog used to interact with the preference
     */
    public static final class NumberPickerPreferenceDialog extends PreferenceDialogFragment {

        public static final String START_RANGE = "startRange";
        public static final String END_RANGE = "endRange";
        public static final String DEFAULT_VALUE = "defaultValue";
        public static final String UNLIMITED_VALUE = "unlimitedValue";
        public static final String HAS_UNLIMITED_VALUE = "hasUnlimitedValue";

        private int startRange;
        private int endRange;
        private int defaultValue;
        private int unlimitedValue;
        private boolean hasUnlimitedValue;
        private EditText input;
        private TextView label;

        public NumberPickerPreferenceDialog() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            startRange = getArguments().getInt(START_RANGE);
            endRange = getArguments().getInt(END_RANGE);
            defaultValue = getArguments().getInt(DEFAULT_VALUE);
            unlimitedValue = getArguments().getInt(UNLIMITED_VALUE);
            hasUnlimitedValue = getArguments().getBoolean(HAS_UNLIMITED_VALUE);
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            // passing anything as a dialog on click listener as we want to change the
            // view on click listener later as not to dismiss the dialog on press
            // and we need the builder to create the button
            builder.setNeutralButton(R.string.reset, this);
        }

        @Override
        protected boolean needInputMethod() {
            return true;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog = super.onCreateDialog(savedInstanceState);
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            input.setText(String.valueOf(defaultValue));
                        }
                    });
                }
            });
            return dialog;
        }

        private long getValue() {
            return getPreference().getSharedPreferences().getLong(getKey(), defaultValue);
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);
            label = (TextView) view.findViewById(R.id.number_picker_label);
            input = (EditText) view.findViewById(R.id.number_picker_input);

            input.setHint(String.valueOf(getValue()));

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    Long value = null;
                    try {
                        value = Long.parseLong(String.valueOf(input.getText()));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                    if (value != null) {
                        if (value >= startRange && value <= endRange) {
                            setEnabledPositiveButton(true);
                            return;
                        }
                    }
                    setEnabledPositiveButton(false);
                }
            });

            if(hasUnlimitedValue){
                label.setText(String.format("Choose a value form %d to %d\n(%d means unlimited, default: %d)", startRange, endRange, unlimitedValue, defaultValue));
            } else {
                label.setText(String.format("Choose a value form %d to %d\n(Default: %d)", startRange, endRange, defaultValue));
            }
        }

        private void setEnabledPositiveButton(boolean enable) {
            ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enable);
        }

        private String getKey() {
            return getPreference().getKey();
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                String text = String.valueOf(input.getText());
                if(!StringUtils.isNullOrEmpty(text)) {
                    int newValue = Integer.parseInt(text);
                    ((NumberPickerPreference2) getPreference()).saveValue(newValue);
                    final Preference.OnPreferenceChangeListener onPreferenceChangeListener = getPreference().getOnPreferenceChangeListener();
                    if (onPreferenceChangeListener != null) {
                        try {
                            onPreferenceChangeListener.onPreferenceChange(getPreference(), newValue);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
            }
        }

        public static DialogFragment newInstance(Preference preference) {
            NumberPickerPreferenceDialog fragment = new NumberPickerPreferenceDialog();
            Bundle bundle = new Bundle(6);
            bundle.putString(ARG_KEY, preference.getKey());
            bundle.putInt(START_RANGE, ((NumberPickerPreference2) preference).getStartRange());
            bundle.putInt(END_RANGE, ((NumberPickerPreference2) preference).getEndRange());
            bundle.putInt(DEFAULT_VALUE, ((NumberPickerPreference2) preference).getDefaultValue());
            bundle.putBoolean(HAS_UNLIMITED_VALUE, ((NumberPickerPreference2) preference).getHasUnlimitedValue());
            bundle.putInt(UNLIMITED_VALUE, ((NumberPickerPreference2) preference).getUnlimitedValue());

            fragment.setArguments(bundle);
            return fragment;
        }
    }
}
