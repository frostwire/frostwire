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
import android.preference.CheckBoxPreference;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;

import java.util.Map;

/**
 * Created on 9/20/16.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 *
 */
public class ToggleAllSearchEnginesPreference2 extends CheckBoxPreference {

    private CheckBox checkbox;
    private Map<CheckBoxPreference, SearchEngine> searchEnginePreferences;
    private boolean clickListenerEnabled;

    public ToggleAllSearchEnginesPreference2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayoutResource(R.layout.view_preference_checkbox_header2);
    }

    public ToggleAllSearchEnginesPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.view_preference_checkbox_header2);
    }

    public CheckBox getCheckbox() {
        return checkbox;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        final TextView titleView = (TextView) view.findViewById(R.id.view_preference_checkbox_header_title);
        titleView.setText(getTitle());

        checkbox = (CheckBox) view.findViewById(R.id.view_preference_checkbox_header_checkbox);
        checkbox.setVisibility(View.VISIBLE);
        checkbox.setClickable(true);
        checkbox.setChecked(areAllEnginesChecked());


        checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListenerEnabled) {
                    checkAllEngines(checkbox.isChecked());
                }
            }
        });

        clickListenerEnabled = true;

        return view;
    }

    public void setClickListenerEnabled(boolean enabled) {
        clickListenerEnabled = enabled;
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

                if (searchEnginePreferences.get(preference).getName().equals("Archive.org")) {
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
        for (CheckBoxPreference preference : searchEnginePreferences.keySet()) {
            if (preference != null) {
                if (!preference.isChecked()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (checkbox != null) {
            checkbox.setChecked(isChecked());
        }
    }

    public void setSearchEnginePreferences(Map<CheckBoxPreference, SearchEngine> searchEnginePreferences) {
        this.searchEnginePreferences = searchEnginePreferences;
    }
}
