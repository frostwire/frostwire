/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
public class CheckBoxPreference extends android.preference.CheckBoxPreference {
    private TextView title;
    private TextView summary;
    private CheckBox checkbox;

    public CheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = View.inflate(getContext(), R.layout.view_checkbox_preference, null);
        title = (TextView) view.findViewById(R.id.checkbox_preference_title);
        summary = (TextView) view.findViewById(R.id.checkbox_preference_summary);
        checkbox = (CheckBox) view.findViewById(R.id.checkbox_preference_checkbox);

        checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBoxPreference.this.setChecked(checkbox.isChecked());
                CheckBoxPreference.this.getOnPreferenceChangeListener().onPreferenceChange(CheckBoxPreference.this,
                        new Boolean(checkbox.isChecked()));
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
