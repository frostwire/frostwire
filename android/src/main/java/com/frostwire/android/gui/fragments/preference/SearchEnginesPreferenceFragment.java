/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.fragments.preference;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NOTE: If you want a search engine to not appear on the list for the basic/google play version see
 * SoftwareUpdater.checkUpdateAsyncPost(), there such engines are de-activated when Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !Constants.IS_BASIC_AND_DEBUG
 *
 * <p>Selection rule: all web engines may be unchecked when
 * {@link Constants#PREF_KEY_SEARCH_USE_DISTRIBUTED Distributed search} is enabled.
 * If Distributed is off, at least one engine on this screen must stay checked
 * (enforced on click and when leaving the screen).
 *
 * @author gubatron
 * @author aldenml
 */
public final class SearchEnginesPreferenceFragment extends AbstractPreferenceFragment {

    public static final String PREF_KEY_SEARCH_SELECT_ALL = "frostwire.prefs.search.preference_category.select_all";

    private final Map<CheckBoxPreference, SearchEngine> activeSearchEnginePreferences;
    private final Map<String, CheckBoxPreference> visibleSearchEnginePreferences;

    public SearchEnginesPreferenceFragment() {
        // See settings_search_engines.xml if you're looking for the layout that has all the checkboxes
        super(R.xml.settings_search_engines);
        activeSearchEnginePreferences = new HashMap<>();
        visibleSearchEnginePreferences = new LinkedHashMap<>();
    }

    @Override
    protected void initComponents() {
        setupSearchEngines();
    }

    @Override
    public void onPause() {
        super.onPause();
        // If Distributed is off, leaving with zero engines selected is not allowed.
        ensureMinimumEngineSelectionIfNeeded();
    }

    private void setupSearchEngines() {
        final CheckBoxPreference selectAllCheckbox = findPreference(PREF_KEY_SEARCH_SELECT_ALL);

        Map<CheckBoxPreference, SearchEngine> inactiveSearchPreferences = new HashMap<>();
        fillSearchEnginePreferences(activeSearchEnginePreferences, inactiveSearchPreferences);

        // click listener for the search engines. Checks or unchecks the SelectAll checkbox
        Preference.OnPreferenceClickListener searchEngineClickListener = preference -> {
            CheckBoxPreference cb = (CheckBoxPreference) preference;

            if (!cb.isChecked()) {
                setChecked(selectAllCheckbox, false, false);
                if (areAllEnginesChecked(false) && !isDistributedSearchEnabled()) {
                    // Distributed off: keep at least one web/local engine selected.
                    cb.setChecked(true);
                    UIUtils.showShortMessage(getView(), R.string.search_preferences_one_engine_checked_always);
                }
                selectAllCheckbox.setTitle(R.string.select_all);
            } else {
                updateSelectAllCheckBox();
            }
            return true;
        };

        // hide inactive search engines and setup click listeners to interact with Select All.
        for (CheckBoxPreference preference : inactiveSearchPreferences.keySet()) {
            getPreferenceScreen().removePreference(preference);
        }

        collectVisibleSearchEnginePreferences();

        for (CheckBoxPreference preference : visibleSearchEnginePreferences.values()) {
            preference.setOnPreferenceClickListener(searchEngineClickListener);
        }

        selectAllCheckbox.setOnPreferenceClickListener(preference -> {
            CheckBoxPreference selectAll1 = (CheckBoxPreference) preference;
            checkAllEngines(selectAll1.isChecked());
            selectAll1.setTitle(selectAll1.isChecked() ? R.string.deselect_all : R.string.select_all);
            return true;
        });
        updateSelectAllCheckBox();
    }

    private void updateSelectAllCheckBox() {
        CheckBoxPreference cb = findPreference(PREF_KEY_SEARCH_SELECT_ALL);
        boolean allChecked = areAllEnginesChecked(true);
        setChecked(cb, allChecked, false);
        cb.setTitle(allChecked ? R.string.deselect_all : R.string.select_all);
    }

    private void fillSearchEnginePreferences(Map<CheckBoxPreference, SearchEngine> active, Map<CheckBoxPreference, SearchEngine> inactive) {
        // make sure we start empty
        inactive.clear();
        active.clear();

        List<SearchEngine> engines = SearchEngine.getEngines(false);
        for (SearchEngine engine : engines) {
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

    private boolean areAllEnginesChecked(boolean checked) {
        for (CheckBoxPreference preference : visibleSearchEnginePreferences.values()) {
            if (checked != preference.isChecked()) {
                return false;
            }
        }
        return true;
    }

    private boolean isAnyEngineChecked() {
        for (CheckBoxPreference preference : visibleSearchEnginePreferences.values()) {
            if (preference.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDistributedSearchEnabled() {
        try {
            return ConfigurationManager.instance()
                    .getBoolean(Constants.PREF_KEY_SEARCH_USE_DISTRIBUTED);
        } catch (Throwable t) {
            return false;
        }
    }

    private void checkAllEngines(boolean checked) {
        for (CheckBoxPreference preference : visibleSearchEnginePreferences.values()) {
            setChecked(preference, checked, false);
        }

        // Deselect-all with Distributed off: leave one engine on.
        if (!checked && !isDistributedSearchEnabled()) {
            ensureOneEngineChecked(true);
        }
    }

    /**
     * When Distributed search is disabled, force at least one engine checkbox on.
     *
     * @param showMessage toast when a forced re-check happens
     * @return true if a selection was forced
     */
    private boolean ensureOneEngineChecked(boolean showMessage) {
        if (isAnyEngineChecked()) {
            return false;
        }
        CheckBoxPreference fallback = visibleSearchEnginePreferences.get(
                Constants.PREF_KEY_SEARCH_USE_ARCHIVEORG);
        if (fallback == null && !visibleSearchEnginePreferences.isEmpty()) {
            fallback = visibleSearchEnginePreferences.values().iterator().next();
        }
        if (fallback == null) {
            return false;
        }
        setChecked(fallback, true, false);
        updateSelectAllCheckBox();
        if (showMessage && getView() != null) {
            UIUtils.showShortMessage(getView(), R.string.search_preferences_one_engine_checked_always);
        }
        return true;
    }

    private void ensureMinimumEngineSelectionIfNeeded() {
        if (isDistributedSearchEnabled()) {
            return;
        }
        ensureOneEngineChecked(true);
    }

    private void collectVisibleSearchEnginePreferences() {
        visibleSearchEnginePreferences.clear();
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen == null) {
            return;
        }

        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            Preference preference = preferenceScreen.getPreference(i);
            if (!(preference instanceof CheckBoxPreference)) {
                continue;
            }
            if (PREF_KEY_SEARCH_SELECT_ALL.equals(preference.getKey())) {
                continue;
            }
            visibleSearchEnginePreferences.put(preference.getKey(), (CheckBoxPreference) preference);
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
            this.unCheckedDrawableId = R.color.basic_background;
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
