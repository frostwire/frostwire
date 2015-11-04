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
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.frostwire.android.R;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class NumberSpinnerPreference extends DialogPreference {

    private int startRange;
    private int endRange;
    private int defaultValue;

    private Spinner spinner;

    public NumberSpinnerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        if (attrs == null) {
            return;
        }

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.NumberSpinnerPreference);
        startRange = arr.getInteger(R.styleable.NumberSpinnerPreference_spinner_startRange, 0);
        endRange = arr.getInteger(R.styleable.NumberSpinnerPreference_spinner_endRange, 200);
        defaultValue = arr.getInteger(R.styleable.NumberSpinnerPreference_spinner_defaultValue, 0);

        arr.recycle();

        setDialogLayoutResource(R.layout.dialog_preference_number_spinner);
    }

    public NumberSpinnerPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public NumberSpinnerPreference(Context context) {
        this(context, null);
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
        case DialogInterface.BUTTON_POSITIVE:
            saveValue((Integer) spinner.getSelectedItem());
            break;
        default:
            break;
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        spinner = (Spinner) view.findViewById(R.id.dialog_preference_number_spinner);

        Integer[] values = new Integer[endRange - startRange + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = startRange + i;
        }

        ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(getValue() - startRange);
    }

    private void saveValue(int val) {
        getEditor().putInt(getKey(), val).commit();
        notifyChanged();
    }

    private int getValue() {
        return getSharedPreferences().getInt(getKey(), defaultValue);
    }
}
