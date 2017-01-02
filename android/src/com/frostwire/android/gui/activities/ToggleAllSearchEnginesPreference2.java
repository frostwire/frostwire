/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.activities;

import android.content.Context;
import android.graphics.Typeface;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SearchEngine;

import java.util.Map;

/**
 * Created on 9/20/16.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 * @author grzesiekrzaca
 */
public class ToggleAllSearchEnginesPreference2 extends CheckBoxPreference {

    private CheckBox checkbox;
    private Map<CheckBoxPreference, SearchEngine> searchEnginePreferences;
    private int backgroundColor;
    private boolean fixStateAfterNextBind = true;

    private View.OnClickListener checkBoxListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ToggleAllSearchEnginesPreference2.this.onClick();
        }
    };

    private Runnable delayedCheckboxCheck = new Runnable() {
        @Override
        public void run() {
            checkbox.setChecked(isChecked());
        }
    };


    public ToggleAllSearchEnginesPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        backgroundColor = context.getResources().getColor(R.color.basic_gray_dark);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setBackgroundColor(backgroundColor);
        TextView title = (TextView) view.findViewById(android.R.id.title);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        checkbox = (CheckBox) view.findViewById(android.R.id.checkbox);
        checkbox.setOnClickListener(checkBoxListener);
        return view;
    }

    private void silentCheck(boolean newValue) {
        checkAllEngines(newValue);
    }

    @Override
    public boolean isChecked() {
        return areAllEnginesChecked();
    }

    private void onClickInternal() {
        checkbox.setChecked(!isChecked());
        ToggleAllSearchEnginesPreference2.this.silentCheck(!isChecked());
    }

    @Override
    protected void onClick() {
        onClickInternal();
        checkbox.getHandler().post(delayedCheckboxCheck);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        setChecked(isChecked());
        if (fixStateAfterNextBind) {
            fixStateAfterNextBind = false;
            checkbox.setChecked(isChecked());
        }
    }

    private void checkAllEngines(boolean checked) {
        if (searchEnginePreferences == null) {
            return;
        }

        final OnPreferenceClickListener onPreferenceClickListener = searchEnginePreferences.keySet().iterator().next().getOnPreferenceClickListener();
        CheckBoxPreference archivePreference = null;

        for (CheckBoxPreference preference : searchEnginePreferences.keySet()) {
            if (preference != null) { //it could already have been removed due to remote config value.
                preference.setOnPreferenceClickListener(null);
                preference.setChecked(checked);
                preference.setOnPreferenceClickListener(onPreferenceClickListener);

                if (searchEnginePreferences.get(preference).getPreferenceKey().equals(Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG)) {
                    archivePreference = preference;
                }
            }
        }

        // always leave one checked.
        if (!checked && archivePreference != null) {
            archivePreference.setOnPreferenceClickListener(null);
            archivePreference.setChecked(true);
            archivePreference.setOnPreferenceClickListener(onPreferenceClickListener);
        }
    }

    private boolean areAllEnginesChecked() {
        if (searchEnginePreferences != null) {
            for (CheckBoxPreference preference : searchEnginePreferences.keySet()) {
                if (preference != null) {
                    if (!preference.isChecked()) {
                        return false;
                    }
                }
            }
        } else {
            fixStateAfterNextBind = true;
        }
        return true;
    }

    private boolean isOneEngineNotChecked() {
        int count = 0;
        if (searchEnginePreferences != null) {
            for (CheckBoxPreference preference : searchEnginePreferences.keySet()) {
                if (preference != null) {
                    if (!preference.isChecked()) {
                        count++;
                    }
                }
            }
            if (count == 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isAtLeastOneOtherEngineChecked(CheckBoxPreference checkBoxPreference) {
        for (CheckBoxPreference preference : searchEnginePreferences.keySet()) {
            if (preference != null && preference != checkBoxPreference) {
                if (preference.isChecked()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setSearchEnginePreferences(Map<CheckBoxPreference, SearchEngine> searchEnginePreferences) {
        this.searchEnginePreferences = searchEnginePreferences;
    }

    /**
     * @return True if the child is allowed to change it's state
     */
    public boolean requestChildStateChange(CheckBoxPreference checkBoxPreference) {
        if (isAtLeastOneOtherEngineChecked(checkBoxPreference)) {//can change the child, update state if needed
            if (checkbox.getHandler() != null) {// only need to animate checkbox if it is visible
                if (areAllEnginesChecked()) {//turn on the checkbox
                    checkbox.getHandler().post(delayedCheckboxCheck);
                } else if (isOneEngineNotChecked()) {//turnoff the check (just unchecked on one of the children)
                    checkbox.getHandler().post(delayedCheckboxCheck);
                }
            } else {
                checkbox.setChecked(isChecked());
            }
            return true;
        } //can't change the child, don't change anything
        return false;
    }
}
