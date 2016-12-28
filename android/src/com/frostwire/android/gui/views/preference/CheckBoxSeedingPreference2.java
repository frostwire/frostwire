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
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 * @author grzesiek
 *         <p>
 *         This is a special preference that allows the text to be greyed-out.
 *         notifyChanged() in Preference breakes the animation of the checkbox.
 *         Preference logic has to be rewritten not to use it. (Manual manipulation of views)
 */
public class CheckBoxSeedingPreference2 extends android.preference.CheckBoxPreference {
    private final ColorStateList titleColor;
    private final ColorStateList summaryColor;
    private TextView title;
    private TextView summary;
    private CheckBox checkbox;
    private Boolean checked;
    private final int summaryOnResId;
    private final int summaryOffResId;


    public CheckBoxSeedingPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        titleColor = context.getResources().getColorStateList(R.color.preference_title_text);
        summaryColor = context.getResources().getColorStateList(R.color.preference_summary_text);
        summaryOffResId = R.string.seed_finished_torrents_wifi_only_summary_off;
        summaryOnResId = R.string.seed_finished_torrents_wifi_only_summary;
        checked = isChecked();
        setSummaryOff("");
        setSummaryOn("");
        setSummary("");
    }


    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        title = (TextView) view.findViewById(android.R.id.title);
        summary = (TextView) view.findViewById(android.R.id.summary);
        checkbox = (CheckBox) view.findViewById(android.R.id.checkbox);

        title.setTextColor(titleColor);
        summary.setTextColor(summaryColor);

        checkbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBoxSeedingPreference2.this.silentCheck(!isChecked());
            }
        });

        return view;
    }

    private void silentCheck(boolean newValue) {
        checked = newValue;
        persistBoolean(newValue);
        callChangeListener(newValue);
        forceSummary(getCustomSummary());
    }

    private int getCustomSummary() {
        return checked ? summaryOnResId : summaryOffResId;
    }

    private void forceSummary(int summaryResId) {
        if (summary != null) {
            summary.setText(summaryResId);
            summary.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean isChecked() {
        return checked != null ? checked : super.isChecked();
    }

    @Override
    protected void onClick() {
        checkbox.setChecked(!isChecked());
        checkbox.callOnClick();
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        this.checked = checked;
        forceSummary(getCustomSummary());
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (checkbox != null) {
            setChecked(isChecked());
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        setChecked(checked);
    }
}
