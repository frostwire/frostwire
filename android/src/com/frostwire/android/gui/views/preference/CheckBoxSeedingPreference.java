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

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 */
public class CheckBoxSeedingPreference extends android.preference.CheckBoxPreference {
    private TextView title;
    private TextView summary;
    private CheckBox checkbox;

    public CheckBoxSeedingPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {

        //for the SettingsActivity2 we will use current
        // View view = View.inflate(getContext(), R.layout.view_preference_checkbox_seeding_2, null);
        View view = View.inflate(getContext(), R.layout.view_preference_checkbox_seeding, null);
        title = (TextView) view.findViewById(R.id.view_preference_checkbox_seeding_title);
        summary = (TextView) view.findViewById(R.id.view_preference_checkbox_seeding_summary);
        checkbox = (CheckBox) view.findViewById(R.id.view_preference_checkbox_seeding_checkbox);

        checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBoxSeedingPreference.this.setChecked(checkbox.isChecked());
                CheckBoxSeedingPreference.this.getOnPreferenceChangeListener().onPreferenceChange(CheckBoxSeedingPreference.this,
                        checkbox.isChecked());
            }
        });

        title.setText(getTitle());
        summary.setText(getSummary());
        return view;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (title != null) {
            title.setEnabled(enabled);
        }
        if (summary != null) {
            summary.setEnabled(enabled);
        }
        super.setEnabled(enabled);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        checkbox.setChecked(isChecked());
    }
}
