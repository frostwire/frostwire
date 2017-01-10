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
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.util.Log;
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
 */
public class CheckBoxSeedingPreference2 extends CheckBoxPreference {
    private final ColorStateList titleColor;
    private final ColorStateList summaryColor;
    private TextView title;
    private TextView summary;
    private CheckBox checkbox;
    private final int summaryOnResId;
    private final int summaryOffResId;


    public CheckBoxSeedingPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        titleColor = context.getResources().getColorStateList(R.color.preference_title_text);
        summaryColor = context.getResources().getColorStateList(R.color.preference_summary_text);
        summaryOffResId = R.string.seed_finished_torrents_wifi_only_summary_off;
        summaryOnResId = R.string.seed_finished_torrents_wifi_only_summary;
        setSummaryOff("");
        setSummaryOn("");
        setSummary("");
    }

    private int getCustomSummary() {
        return isChecked() ? summaryOnResId : summaryOffResId;
    }

    private void forceSummary(int summaryResId) {
        if (summary != null) {
            summary.setText(summaryResId);
            summary.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onClick() {
        checkbox.setChecked(!isChecked());
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        title = (TextView) holder.findViewById(android.R.id.title);
        summary = (TextView) holder.findViewById(android.R.id.summary);
        checkbox = (CheckBox) holder.findViewById(android.R.id.checkbox);

        title.setTextColor(titleColor);
        summary.setTextColor(summaryColor);

        forceSummary(getCustomSummary());
    }

}
