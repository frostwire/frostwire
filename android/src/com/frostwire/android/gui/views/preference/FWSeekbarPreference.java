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
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;

import java.lang.reflect.Field;

/**
 * Support version of a custom dialog preference
 *
 * @author grzesiekrzaca
 * @author gubatron
 */
public final class FWSeekbarPreference extends DialogPreference {

    private final int startRange;
    private final int endRange;
    private final int defaultValue;
    private final boolean isByteRate;
    private final int pluralUnitResourceId;
    private final boolean hasUnlimited;
    private final int unlimitedValue;

    public FWSeekbarPreference(Context context, AttributeSet attrs) {

        super(context, attrs);
        setDialogLayoutResource(R.layout.dialog_preference_seekbar_with_checkbox);
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.fwSeekbarPreference);
        startRange = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_startRange, 0);
        endRange = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_endRange, 100);
        defaultValue = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_defaultValue, 0);
        isByteRate = arr.getBoolean(R.styleable.fwSeekbarPreference_seekbar_isByteRate, false);
        hasUnlimited = arr.getBoolean(R.styleable.fwSeekbarPreference_seekbar_hasUnlimited, false);
        unlimitedValue = arr.getInteger(R.styleable.fwSeekbarPreference_seekbar_unlimitedValue, 0);
        pluralUnitResourceId = getPluralUnitResourceId(arr);
        arr.recycle();
    }

    /**
     * ANDROID API EDGE FOUND: @plurals/my_plural is not supported in XML layouts
     * supposedly because of translation support issues, I don't see why, as you coul
     * have multiple plurals.xml defined, one per each language.
     * For now, we just put the name of the plural field in the XML layout and we
     * use reflection to fetch it from the R.java file.
     *
     * See:
     *   - plurals.xml
     *   - attrs.xml (fwSeekbarPreference::seekbar_pluralUnitResourceIdName)
     *   - settings_torrents.xml
     *   - frostwire.prefs.torrent.max_downloads and others below which use units (non byte rates)
     */
    private int getPluralUnitResourceId(TypedArray arr) {
        String pluralUnitResourceIdName = arr.getString(R.styleable.fwSeekbarPreference_seekbar_pluralUnitResourceIdName);
        int pluralUnitResourceIdTemp = -1;
        if (pluralUnitResourceIdName != null) {
            try {
                Class<?> pluralsClass = Class.forName("com.frostwire.android.R$plurals");
                Field declaredField = pluralsClass.getDeclaredField(pluralUnitResourceIdName);
                pluralUnitResourceIdTemp = declaredField.getInt(null);
            } catch (Throwable t) {
                t.printStackTrace();
                pluralUnitResourceIdTemp = -1;
            }
        }
        return pluralUnitResourceIdTemp;
    }

    public void saveValue(long val) {
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

    public boolean isByteRate() {
        return isByteRate;
    }

    public int getPluralUnitResourceId() {
        return pluralUnitResourceId;
    }

    public boolean supportsUnlimitedValue() {
        return hasUnlimited;
    }

    public int getUnlimitedValue() {
        return unlimitedValue;
    }

    public static final class FWSeekbarPreferenceDialog extends AbstractPreferenceFragment.PreferenceDialogFragment {
        private static final String START_RANGE = "startRange";
        private static final String END_RANGE = "endRange";
        private static final String DEFAULT_VALUE = "defaultValue";
        private static final String IS_BYTE_RATE = "isByteRate";
        private static final String PLURAL_UNIT_RESOURCE_ID = "pluralUnitResourceId";
        private static final String SUPPORTS_UNLIMITED = "supportsUnlimited";
        private static final String UNLIMITED_VALUE = "unlimitedValue";
        private static final String UNLIMITED_CHECKED = "unlimitedChecked";
        private static final String CURRENT_VALUE = "currentValue";

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
            bundle.putInt(CURRENT_VALUE, -1);
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
            outState.putBoolean(UNLIMITED_CHECKED, mUnlimitedCheckbox.isChecked());
            outState.putInt(CURRENT_VALUE, mSeekbar.getProgress());
            super.onSaveInstanceState(outState);
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);
            mSeekbar = (SeekBar) view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_seekbar);
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
            mCurrentValueTextView = (TextView) view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_current_value_textview);
            mUnlimitedCheckbox = (CheckBox) view.findViewById(R.id.dialog_preference_seekbar_with_checkbox_unlimited_checkbox);
            Bundle arguments = getArguments();
            mUnlimitedCheckbox.setChecked((arguments != null && arguments.getBoolean(UNLIMITED_CHECKED)));
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
}
