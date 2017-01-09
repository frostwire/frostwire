/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.views;

import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v7.preference.Preference;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractPreferenceFragment extends PreferenceFragment {

    private final int preferencesResId;

    public AbstractPreferenceFragment(int preferencesResId) {
        this.preferencesResId = preferencesResId;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(preferencesResId);
        initComponents();
    }

    protected void initComponents() {
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Preference> T findPreference(String key) {
        return (T) super.findPreference(key);
    }

    protected final <T extends Preference> T getPreference(String key) {
        T pref = findPreference(key);
        if (pref == null)
            throw new NullPointerException("preference not found with key: " + key);
        return pref;
    }
}
