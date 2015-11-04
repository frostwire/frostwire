/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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
import android.content.res.TypedArray;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SimpleActionPreference extends Preference {

    private CharSequence title;
    private CharSequence summary;
    private CharSequence buttonText;

    private TextView textTitle;
    private TextView textSummary;
    private Button button;

    private OnClickListener listener;

    public SimpleActionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.SimpleActionPreference);

        buttonText = attributes.getString(R.styleable.SimpleActionPreference_button_text);
        attributes.recycle();
    }

    @Override
    public void setTitle(CharSequence title) {
        this.title = title;
        if (textTitle != null) {
            textTitle.setText(title);
        }
    }

    @Override
    public void setSummary(CharSequence summary) {
        this.summary = summary;
        if (textSummary != null) {
            textSummary.setText(summary);
        }
    }

    public void setButtonText(CharSequence text) {
        this.buttonText = text;
        if (button != null) {
            button.setText(buttonText);
        }
    }

    public void setButtonText(int resId) {
        this.buttonText = getContext().getString(resId);
        if (button != null) {
            button.setText(resId);
        }
    }

    public void setButtonEnabled(boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    public void setOnActionListener(OnClickListener listener) {
        this.listener = listener;
        if (button != null) {
            button.setOnClickListener(listener);
        }
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View v = View.inflate(getContext(), R.layout.view_preference_simple_action, null);

        textTitle = (TextView) v.findViewById(R.id.view_preference_simple_action_title_text);
        textTitle.setText(title != null ? title : getTitle());

        textSummary = (TextView) v.findViewById(R.id.view_preference_simple_action_summary_text);
        textSummary.setText(summary != null ? summary : getSummary());

        button = (Button) v.findViewById(R.id.view_preference_simple_action_button);
        button.setText(buttonText);
        if (listener != null) {
            button.setOnClickListener(listener);
        }

        return v;
    }
}
