/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.fragments.preference;

import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;

import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchFragment extends AbstractPreferenceFragment {

    private final Map<CheckBoxPreference, SearchEngine> activeSearchEnginePreferences;

    public SearchFragment() {
        super(R.xml.settings_search);
        activeSearchEnginePreferences = new HashMap<>();
    }

    @Override
    protected void initComponents() {
        setupSearchEngines();
    }

    private void setupSearchEngines() {
        final CheckBoxPreference selectAll = findPreference("frostwire.prefs.search.preference_category.select_all");

        Map<CheckBoxPreference, SearchEngine> inactiveSearchPreferences = new HashMap<>();
        fillSearchEnginePreferences(activeSearchEnginePreferences, inactiveSearchPreferences);

        // click listener for the search engines. Checks or unchecks the SelectAll checkbox
        Preference.OnPreferenceClickListener searchEngineClickListener = new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CheckBoxPreference cb = (CheckBoxPreference) preference;

                if (!cb.isChecked()) {
                    setChecked(selectAll, false, false);
                    if (areAllEnginesChecked(activeSearchEnginePreferences, false)) {
                        cb.setChecked(true); // always keep one checked
                        UIUtils.showShortMessage(getView(), R.string.search_preferences_one_engine_checked_always);
                    }
                    selectAll.setTitle(R.string.select_all);
                } else {
                    updateSelectAllCheckBox();
                }
                return true;
            }
        };

        // hide inactive search engines and setup click listeners to interact with Select All.
        for (CheckBoxPreference preference : inactiveSearchPreferences.keySet()) {
            getPreferenceScreen().removePreference(preference);
        }

        for (CheckBoxPreference preference : activeSearchEnginePreferences.keySet()) {
            preference.setOnPreferenceClickListener(searchEngineClickListener);
        }

        selectAll.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CheckBoxPreference selectAll = (CheckBoxPreference) preference;
                checkAllEngines(selectAll.isChecked());
                selectAll.setTitle(selectAll.isChecked() ? R.string.deselect_all : R.string.select_all);
                return true;
            }
        });
        updateSelectAllCheckBox();
    }

    private void updateSelectAllCheckBox() {
        CheckBoxPreference cb = findPreference("frostwire.prefs.search.preference_category.select_all");
        boolean allChecked = areAllEnginesChecked(activeSearchEnginePreferences, true);
        setChecked(cb, allChecked, false);
        cb.setTitle(allChecked ? R.string.deselect_all : R.string.select_all);
    }

    private void fillSearchEnginePreferences(Map<CheckBoxPreference, SearchEngine> active, Map<CheckBoxPreference, SearchEngine> inactive) {
        // make sure we start empty
        inactive.clear();
        active.clear();

        for (SearchEngine engine : SearchEngine.getEngines()) {
            CheckBoxPreference preference = findPreference(engine.getPreferenceKey());
            if (preference != null) { //it could already have been removed due to remote config value.
                if (engine.isActive()) {
                    active.put(preference, engine);
                } else {
                    inactive.put(preference, engine);
                }
            }
        }
    }

    private boolean areAllEnginesChecked(Map<CheckBoxPreference, SearchEngine> map, boolean checked) {
        for (CheckBoxPreference preference : map.keySet()) {
            if (checked != preference.isChecked()) {
                return false;
            }
        }
        return true;
    }

    private void checkAllEngines(boolean checked) {
        CheckBoxPreference archivePreference = null;

        for (CheckBoxPreference preference : activeSearchEnginePreferences.keySet()) {
            if (preference != null) { //it could already have been removed due to remote config value.
                setChecked(preference, checked, false);

                if (activeSearchEnginePreferences.get(preference).getName().equals("Archive.org")) {
                    archivePreference = preference;
                }
            }
        }

        // always leave one checked.
        if (!checked && archivePreference != null) {
            setChecked(archivePreference, true, false);
            UIUtils.showShortMessage(getView(), R.string.search_preferences_one_engine_checked_always);
        }
    }
}
