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
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.frostwire.android.gui.SearchEngine;

import java.util.Map;

/**
 * Created on 9/20/16.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class ToggleAllSearchEnginesPreference2 extends CheckBoxPreference {

    //    private TextView title;
//    private TextView summary;
    private CheckBox checkbox;
    private boolean checked;
    private Map<CheckBoxPreference, SearchEngine> searchEnginePreferences;

    private View.OnClickListener checkBoxListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.w("DBG","checkBox.onClick pref: "+isChecked() + " cb: " + checkbox.isChecked());
            ToggleAllSearchEnginesPreference2.this.onClick();
//            ToggleAllSearchEnginesPreference2.this.silentCheck(!isChecked());
        }
    };
    private boolean notifyChildren = true;
    private boolean fix = false;


    public ToggleAllSearchEnginesPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        checked = isChecked();
    }



    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);

        Log.w("DBG","createview");
//        title = (TextView) view.findViewById(android.R.id.title);
//        summary = (TextView) view.findViewById(android.R.id.summary);
        checkbox = (CheckBox) view.findViewById(android.R.id.checkbox);

        checkbox.setOnClickListener(checkBoxListener);

        return view;
    }

    private void silentCheck(boolean newValue) {
        Log.w("DBG","silent check "+newValue);
        checked = newValue;
        persistBoolean(newValue);
        if(notifyChildren) {
            checkAllEngines(newValue);
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    protected void onClickInternal() {
        Log.w("DBG", "onClickInternal");
        checkbox.setChecked(!isChecked());
        ToggleAllSearchEnginesPreference2.this.silentCheck(!isChecked());
        Log.w("DBG", "OnClickInternalEnd");
    }

    @Override
    protected void onClick() {
        onClickInternal();
        checkbox.getHandler().post(new Runnable() {
            @Override
            public void run() {
                Log.w("DBG", "Runnable cb "+checkbox.isChecked() + " pref "+isChecked() );
                //this is for syncing checkbox visual state required after recreation due to changes from other preferences - not that great todo find better way
                if(!checkbox.isChecked() && !isChecked()) {
                    Log.w("DBG", "special case, setting checkbox "+!isChecked() );
                    checkbox.setChecked(!isChecked());
                }
                if(checkbox.isChecked() && isChecked()) {
                    Log.w("DBG", "special case, setting checkbox "+!isChecked() );
                    checkbox.setChecked(!isChecked());
                }
                checkbox.setChecked(isChecked());
            }
        });
        Log.w("DBG", "OnClickEnd");
    }

    @Override
    public void setChecked(boolean checked) {
        Log.w("DBG", this.checked + " -set-> "+checked);
        super.setChecked(checked);
        this.checked = checked;
    }

    @Override
    protected void onBindView(View view) {
        Log.w("DBG", "bind");
        super.onBindView(view);
        setChecked(isChecked());
        //fix transition
        if(fix){

            fix=false;
        }
    }

    private void checkAllEngines(boolean checked) {
        if (searchEnginePreferences == null) {
            return;
        }

        Log.w("DBG","checkall");
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
        Log.w("DBG", "req "+checkBoxPreference);
        if (isAtLeastOneOtherEngineChecked(checkBoxPreference)) {
            //can change the child
            if(areAllEnginesChecked()){//all engines checked
                setChecked(false);
                checkbox.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        Log.w("DBG","running onClick when all are checked [after press]");
                        notifyChildren=false;
                        ToggleAllSearchEnginesPreference2.this.onClickInternal();
                        notifyChildren=true;
                    }
                });
            } else {
                if(isChecked()) {//turnoff the check
                    setChecked(true);
                    checkbox.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Log.w("DBG","running onClick when not all are checked [after press] and this is checked");
                            notifyChildren=false;
                            ToggleAllSearchEnginesPreference2.this.onClickInternal();
                            notifyChildren=true;
                        }
                    });
                } else { // don't change it
                    setChecked(false);
                    Log.w("DBG","doing nothing when not all are checked and this is not checked");
                }
            }
            return true;
        } //cant change the child, don't change anything
        return false;
    }
}
