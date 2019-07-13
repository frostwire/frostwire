/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment.PreferenceDialogFragment;

import androidx.annotation.NonNull;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Support version of a custom dialog preference
 *
 * @author grzesiekrzaca
 * @author gubatron
 */
public final class CustomSeekBarPreference extends DialogPreference {

    private final int startRange;
    private final int endRange;
    private final int defaultValue;
    private final boolean isByteRate;
    private final int pluralUnitResourceId;
    private final boolean hasUnlimited;
    private final int unlimitedValue;

    public CustomSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.dialog_preference_seekbar_with_checkbox);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.fwSeekbarPreference);
        startRange = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_startRange, 0);
        endRange = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_endRange, 100);
        defaultValue = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_defaultValue, 0);
        isByteRate = arr.getBoolean(R.styleable.fwSeekbarPreference_seekbar_isByteRate, false);
        hasUnlimited = arr.getBoolean(R.styleable.fwSeekbarPreference_seekbar_hasUnlimited, false);
        unlimitedValue = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_unlimitedValue, 0);
        pluralUnitResourceId = arr.getResourceId(R.styleable.fwSeekbarPreference_seekbar_pluralUnitResourceId, 0);
        arr.recycle();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        if (summaryView != null) {
            setNumericalSummary(summaryView, getPersistedLong(defaultValue));
        }
    }

    private boolean isByteRate() {
        return isByteRate;
    }

    private int getPluralUnitResourceId() {
        return pluralUnitResourceId;
    }

    private boolean supportsUnlimitedValue() {
        return hasUnlimited;
    }

    private int getUnlimitedValue() {
        return unlimitedValue;
    }

    private void saveValue(long val) {
        persistLong(val);
        notifyChanged();
    }

    private int getStartRange() {
        return startRange;
    }

    private int getEndRange() {
        return endRange;
    }

    private int getDefaultValue() {
        return defaultValue;
    }

    private void setNumericalSummary(TextView summaryView, long value) {
        if (supportsUnlimitedValue() && value == getUnlimitedValue()) {
            summaryView.setText(R.string.unlimited);
        } else {
            if (isByteRate()) {
                summaryView.setText(UIUtils.getBytesInHuman(value));
            } else if (getPluralUnitResourceId() != 0) {
                String text = getContext().getResources().
                        getQuantityString(getPluralUnitResourceId(), (int) value, value);
                summaryView.setText(text);
            }
        }
    }

    public static final class CustomSeekBarPreferenceDialog extends PreferenceDialogFragment {

        private static final String START_RANGE = "startRange";
        private static final String END_RANGE = "endRange";
        private static final String DEFAULT_VALUE = "defaultValue";
        private static final String IS_BYTE_RATE = "isByteRate";
        private static final String PLURAL_UNIT_RESOURCE_ID = "pluralUnitResourceId";
        private static final String SUPPORTS_UNLIMITED = "supportsUnlimited";
        private static final String UNLIMITED_VALUE = "unlimitedValue";
        private static final String UNLIMITED_CHECKED = "unlimitedChecked";
        private static final String CURRENT_VALUE = "currentValue";
        private static final String DIALOG_TITLE = "dialogTitle";

        private int mStartRange;
        private int mEndRange;
        private int mDefault;
        private boolean mIsByteRate;
        private int mPluralUnitResourceId;
        private boolean mSupportsUnlimited;
        private int mUnlimitedValue;
        private boolean mSkipListeners;
        private SeekBar mSeekbar;
        private CheckBox mUnlimitedCheckbox;
        private TextView mCurrentValueTextView;
        private TextView mDialogTitleTextView;

        public static CustomSeekBarPreferenceDialog newInstance(final CustomSeekBarPreference preference) {
            CustomSeekBarPreferenceDialog fragment = new CustomSeekBarPreferenceDialog();
            Bundle bundle = new Bundle();
            bundle.putString(ARG_KEY, preference.getKey());
            bundle.putInt(START_RANGE, preference.getStartRange());
            bundle.putInt(END_RANGE, preference.getEndRange());
            bundle.putInt(DEFAULT_VALUE, preference.getDefaultValue());
            bundle.putBoolean(IS_BYTE_RATE, preference.isByteRate());
            bundle.putInt(PLURAL_UNIT_RESOURCE_ID, preference.getPluralUnitResourceId());
            bundle.putBoolean(SUPPORTS_UNLIMITED, preference.supportsUnlimitedValue());
            bundle.putInt(UNLIMITED_VALUE, preference.getUnlimitedValue());
            bundle.putInt(CURRENT_VALUE, -1);
            bundle.putCharSequence(DIALOG_TITLE, preference.getDialogTitle());
            fragment.setArguments(bundle);
            return fragment;
        }

        public CustomSeekBarPreferenceDialog() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle args = getArguments();
            mStartRange = args.getInt(START_RANGE);
            mEndRange = args.getInt(END_RANGE);
            mDefault = args.getInt(DEFAULT_VALUE);
            mIsByteRate = args.getBoolean(IS_BYTE_RATE);
            mPluralUnitResourceId = args.getInt(PLURAL_UNIT_RESOURCE_ID);
            mSupportsUnlimited = args.getBoolean(SUPPORTS_UNLIMITED);
            mUnlimitedValue = args.getInt(UNLIMITED_VALUE);
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(START_RANGE, mStartRange);
            outState.putInt(END_RANGE, mEndRange);
            outState.putInt(DEFAULT_VALUE, mDefault);
            outState.putBoolean(IS_BYTE_RATE, mIsByteRate);
            outState.putInt(PLURAL_UNIT_RESOURCE_ID, mPluralUnitResourceId);
            outState.putBoolean(SUPPORTS_UNLIMITED, mSupportsUnlimited);
            outState.putInt(UNLIMITED_VALUE, mUnlimitedValue);
            outState.putBoolean(UNLIMITED_CHECKED, mUnlimitedCheckbox.isChecked());
            outState.putInt(CURRENT_VALUE, mSeekbar.getProgress());
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);
            mSeekbar = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
            mSeekbar.setMax(mEndRange);

            int previousValue = (int) getPreference().getSharedPreferences().getLong(getKey(), mDefault);
            if (getArguments() != null) {
                int curVal = getArguments().getInt(CURRENT_VALUE);
                if (curVal != -1) {
                    previousValue = curVal;
                }
            }
            mSeekbar.setProgress(previousValue);
            mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    onSeekbarChanged(seekBar, i);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            mCurrentValueTextView = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_current_value_textview);
            mUnlimitedCheckbox = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);

            Bundle arguments = getArguments();
            mDialogTitleTextView = view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_title);
            if (arguments != null) {
                mDialogTitleTextView.setText(arguments.getCharSequence(DIALOG_TITLE));
                mUnlimitedCheckbox.setChecked(arguments.getBoolean(UNLIMITED_CHECKED));
            } else {
                mUnlimitedCheckbox.setChecked(false);
            }
            mUnlimitedCheckbox.setOnClickListener(view1 -> onUnlimitedCheckboxClicked());
            updateComponents(previousValue);
            updateCurrentValueTextView(previousValue);
        }

        private void updateComponents(int currentValue) {
            mSkipListeners = true;
            if (!mSupportsUnlimited) {
                mUnlimitedCheckbox.setVisibility(View.GONE);
                mSeekbar.setEnabled(true);
                mSeekbar.setProgress(currentValue);
            } else {
                mUnlimitedCheckbox.setVisibility(View.VISIBLE);
                mSeekbar.setEnabled(true);
                if (mUnlimitedCheckbox.isChecked()) {
                    mSeekbar.setProgress(mSeekbar.getMax());
                    mSeekbar.setEnabled(false);
                } else {
                    boolean isUnlimited = currentValue == mUnlimitedValue;
                    if (isUnlimited) {
                        mSeekbar.setProgress(mSeekbar.getMax());
                        mUnlimitedCheckbox.setChecked(true);
                        mSeekbar.setEnabled(false);
                    }
                }
            }
            mSkipListeners = false;
        }

        private void onSeekbarChanged(SeekBar seekBar, int value) {
            if (mSkipListeners) {
                return;
            }
            value = seekbarMinValueCheck(seekBar, value);
            Bundle arguments = getArguments();
            arguments.putInt(CURRENT_VALUE, value);
            updateCurrentValueTextView(value);
        }

        // SeekBar does not support a minimum value .setMinimum(int), have to override behaviour
        private int seekbarMinValueCheck(SeekBar seekBar, int value) {
            if (value < mStartRange) {
                mSkipListeners = true;
                value = mStartRange;
                seekBar.setProgress(value);
                mSkipListeners = false;
            }
            return value;
        }

        private void onUnlimitedCheckboxClicked() {
            Bundle arguments = getArguments();
            arguments.putBoolean(UNLIMITED_CHECKED, mUnlimitedCheckbox.isChecked());
            if (mSkipListeners) {
                return;
            }
            mSkipListeners = true;
            int seekbarValue = mSeekbar.getProgress();
            int currentValue = mUnlimitedCheckbox.isChecked() ? mUnlimitedValue : seekbarValue;
            updateComponents(currentValue);
            updateCurrentValueTextView(currentValue);
            mSkipListeners = false;
        }

        private void updateCurrentValueTextView(int value) {
            if (mSupportsUnlimited && (value == mUnlimitedValue) || mUnlimitedCheckbox.isChecked()) {
                mCurrentValueTextView.setText(getResources().getText(R.string.unlimited));
            } else if (mIsByteRate) {
                mCurrentValueTextView.setText(String.format("%s/s", UIUtils.getBytesInHuman(value)));
            } else if (mPluralUnitResourceId != -1) {
                mCurrentValueTextView.setText(getResources().getQuantityString(mPluralUnitResourceId, value, value));
            }
        }

        private String getKey() {
            return getPreference().getKey();
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            if (positiveResult) {
                seekbarMinValueCheck(mSeekbar, mSeekbar.getProgress());
                int value = (mSupportsUnlimited && mUnlimitedCheckbox.isChecked()) ?
                        mUnlimitedValue :
                        mSeekbar.getProgress();
                ((CustomSeekBarPreference) getPreference()).saveValue(value);
                final Preference.OnPreferenceChangeListener onPreferenceChangeListener = getPreference().getOnPreferenceChangeListener();
                if (onPreferenceChangeListener != null) {
                    try {
                        onPreferenceChangeListener.onPreferenceChange(getPreference(), value);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
    }
}
