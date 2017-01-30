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

package com.frostwire.android.gui.dialogs;

import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.FWSeekbarPreference;

/**
 * Support version of a custom dialog preference
 *
 * @author grzesiekrzaca
 * @author gubatron
 */
public final class FWSeekbarPreferenceDialog extends AbstractPreferenceFragment.PreferenceDialogFragment {
    private static final String START_RANGE = "startRange";
    private static final String END_RANGE = "endRange";
    private static final String DEFAULT_VALUE = "defaultValue";
    private static final String IS_BYTE_RATE = "isByteRate";
    private static final String PLURAL_UNIT_RESOURCE_ID = "pluralUnitResourceId";
    private static final String SUPPORTS_UNLIMITED = "supportsUnlimited";
    private static final String UNLIMITED_VALUE = "unlimitedValue";

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

    public static FWSeekbarPreferenceDialog newInstance(final FWSeekbarPreference preference) {
        FWSeekbarPreferenceDialog fragment = new FWSeekbarPreferenceDialog();
        Bundle bundle = new Bundle();
        bundle.putString(ARG_KEY, preference.getKey());
        bundle.putInt(START_RANGE, preference.getStartRange());
        bundle.putInt(END_RANGE, preference.getEndRange());
        bundle.putInt(DEFAULT_VALUE, preference.getDefaultValue());
        bundle.putBoolean(IS_BYTE_RATE, preference.isByteRate());
        bundle.putInt(PLURAL_UNIT_RESOURCE_ID, preference.getPluralUnitResourceId());
        bundle.putBoolean(SUPPORTS_UNLIMITED, preference.supportsUnlimitedValue());
        bundle.putInt(UNLIMITED_VALUE, preference.getUnlimitedValue());

        fragment.setArguments(bundle);
        return fragment;
    }

    public FWSeekbarPreferenceDialog() {
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
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(START_RANGE, mStartRange);
        outState.putInt(END_RANGE, mEndRange);
        outState.putInt(DEFAULT_VALUE, mDefault);
        outState.putBoolean(IS_BYTE_RATE, mIsByteRate);
        outState.putInt(PLURAL_UNIT_RESOURCE_ID, mPluralUnitResourceId);
        outState.putBoolean(SUPPORTS_UNLIMITED, mSupportsUnlimited);
        outState.putInt(UNLIMITED_VALUE, mUnlimitedValue);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        int previousValue = (int) getPreference().getSharedPreferences().getLong(getKey(), mDefault);
        mSeekbar = (SeekBar) view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
        mSeekbar.setMax(mEndRange);
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
        mCurrentValueTextView = (TextView) view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_current_value_textview);
        mUnlimitedCheckbox = (CheckBox) view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);
        mUnlimitedCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUnlimitedCheckboxClicked();
            }
        });
        updateComponents(previousValue);
        updateCurrentValueTextView(previousValue);
    }

    private void updateComponents(int currentValue) {
        mSkipListeners = true;
        mSeekbar.setProgress(currentValue);
        if (!mSupportsUnlimited) {
            mUnlimitedCheckbox.setVisibility(View.GONE);
            mSeekbar.setEnabled(true);
        } else {
            mUnlimitedCheckbox.setVisibility(View.VISIBLE);
            if (currentValue == mUnlimitedValue) {
                mSeekbar.setProgress(mSeekbar.getMax());
                mUnlimitedCheckbox.setChecked(true);
            }
            mSeekbar.setEnabled(currentValue != mUnlimitedValue);
        }
        mSkipListeners = false;
    }

    private void onSeekbarChanged(SeekBar seekBar, int value) {
        if (mSkipListeners) {
            return;
        }
        value = seekbarMinValueCheck(seekBar, value);
        updateCurrentValueTextView(value);
    }

    // SeekBar does not support a minimum value, have to override behaviour
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
        if (mSkipListeners) {
            return;
        }
        mSkipListeners = true;
        mSeekbar.setEnabled(!mUnlimitedCheckbox.isChecked());
        int seekbarValue = mSeekbar.getProgress();
        updateComponents(seekbarValue);
        updateCurrentValueTextView(seekbarValue);
        mSkipListeners = false;
    }

    private void updateCurrentValueTextView(int value) {
        if (mSupportsUnlimited && value == mUnlimitedValue) {
            mCurrentValueTextView.setText(getResources().getText(R.string.unlimited));
        } else if (mIsByteRate) {
            mCurrentValueTextView.setText(UIUtils.getBytesInHuman(value) + "/s");
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
            ((FWSeekbarPreference) getPreference()).saveValue(value);
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
