/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View.OnClickListener;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ButtonActionPreference extends Preference {

    private final CharSequence buttonText;

    private Button button;

    private OnClickListener listener;

    public ButtonActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setLayoutResource(R.layout.view_preference_button_action);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ButtonActionPreference);
        buttonText = attributes.getString(R.styleable.ButtonActionPreference_button_text);
        attributes.recycle();
    }

    public void setOnActionListener(OnClickListener listener) {
        this.listener = listener;
        if (button != null) {
            button.setOnClickListener(listener);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        button = (Button) holder.findViewById(R.id.view_preference_button_action_button);
        button.setText(buttonText);
        if (listener != null) {
            button.setOnClickListener(listener);
        }
    }
}
