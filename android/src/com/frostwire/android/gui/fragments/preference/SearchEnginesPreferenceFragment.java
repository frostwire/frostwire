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

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

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
public final class SearchEnginesPreferenceFragment extends AbstractPreferenceFragment {

    public static final String PREF_KEY_SEARCH_SELECT_ALL = "frostwire.prefs.search.preference_category.select_all";

    private final Map<CheckBoxPreference, SearchEngine> activeSearchEnginePreferences;

    public SearchEnginesPreferenceFragment() {
        super(R.xml.settings_search_engines);
        activeSearchEnginePreferences = new HashMap<>();
    }

    @Override
    protected void initComponents() {
        setupSearchEngines();
    }

    private void setupSearchEngines() {
        final CheckBoxPreference selectAll = findPreference(PREF_KEY_SEARCH_SELECT_ALL);

        Map<CheckBoxPreference, SearchEngine> inactiveSearchPreferences = new HashMap<>();
        fillSearchEnginePreferences(activeSearchEnginePreferences, inactiveSearchPreferences);

        // click listener for the search engines. Checks or unchecks the SelectAll checkbox
        Preference.OnPreferenceClickListener searchEngineClickListener = preference -> {
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
        };

        // hide inactive search engines and setup click listeners to interact with Select All.
        for (CheckBoxPreference preference : inactiveSearchPreferences.keySet()) {
            getPreferenceScreen().removePreference(preference);
        }

        for (CheckBoxPreference preference : activeSearchEnginePreferences.keySet()) {
            preference.setOnPreferenceClickListener(searchEngineClickListener);
        }

        selectAll.setOnPreferenceClickListener(preference -> {
            CheckBoxPreference selectAll1 = (CheckBoxPreference) preference;
            checkAllEngines(selectAll1.isChecked());
            selectAll1.setTitle(selectAll1.isChecked() ? R.string.deselect_all : R.string.select_all);
            return true;
        });
        updateSelectAllCheckBox();
    }

    private void updateSelectAllCheckBox() {
        CheckBoxPreference cb = findPreference(PREF_KEY_SEARCH_SELECT_ALL);
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

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new CheckedAwarePreferenceGroupAdapter(preferenceScreen);
    }

    private static class CheckedAwarePreferenceGroupAdapter extends PreferenceGroupAdapter {
        final int checkedDrawableId;
        final int unCheckedDrawableId;

        CheckedAwarePreferenceGroupAdapter(PreferenceGroup preferenceGroup) {
            super(preferenceGroup);
            this.checkedDrawableId = R.color.app_selection_background;
            this.unCheckedDrawableId = R.color.basic_white;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder, int position) {
            final CheckBoxPreference preference = (CheckBoxPreference) getItem(position);
            preference.onBindViewHolder(holder);
            if (!preference.getKey().equals(PREF_KEY_SEARCH_SELECT_ALL)) {
                int drawableId = preference.isChecked() ? checkedDrawableId : unCheckedDrawableId;
                holder.itemView.setBackgroundResource(drawableId);
            }
        }
    }
}
