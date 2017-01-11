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

import android.app.DialogFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.SwitchPreferenceCompat;

import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.KitKatStoragePreference;
import com.frostwire.android.gui.views.preference.KitKatStoragePreference.KitKatStoragePreferenceDialog;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ApplicationFragment extends AbstractPreferenceFragment {

    public ApplicationFragment() {
        super(R.xml.settings_application);
    }

    @Override
    protected void initComponents() {
        setupConnectSwitch();
        setupStorageOption();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment dlg = null;
        if (preference instanceof KitKatStoragePreference) {
            dlg = KitKatStoragePreferenceDialog.newInstance(preference.getKey());
        }

        if (dlg != null) {
            dlg.setTargetFragment(this, 0);
            dlg.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private void setupConnectSwitch() {
        SwitchPreferenceCompat preference = findPreference("frostwire.prefs.internal.connect_disconnect");
        preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean newStatus = (boolean) newValue;
                Engine e = Engine.instance();
                if (e.isStarted() && !newStatus) {
                    disconnect();
                } else if (newStatus && (e.isStopped() || e.isDisconnected())) {
                    connect();
                }
                return true;
            }
        });

        updateConnectSwitchStatus();
    }

    private void setupStorageOption() {
        // intentional repetition of preference value here
        String kitkatKey = "frostwire.prefs.storage.path";
        String lollipopKey = "frostwire.prefs.storage.path_asf";

        PreferenceCategory category = findPreference("frostwire.prefs.general");

        if (AndroidPlatform.saf()) {
            // make sure this won't be saved for kitkat
            Preference p = findPreference(kitkatKey);
            if (p != null) {
                category.removePreference(p);
            }
            p = findPreference(lollipopKey);
            if (p != null) {
                p.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        updateStorageOptionSummary(newValue.toString());
                        return true;
                    }
                });
                updateStorageOptionSummary(ConfigurationManager.instance().getStoragePath());
            }
        } else {
            Preference p = findPreference(lollipopKey);
            if (p != null) {
                category.removePreference(p);
            }
        }
    }

    private void updateConnectSwitchStatus() {
        SwitchPreferenceCompat preference = findPreference("frostwire.prefs.internal.connect_disconnect");
        Engine e = Engine.instance();
        if (e.isStarted()) {
            setChecked(preference, true, false);
        } else if (e.isStopped() || e.isDisconnected()) {
            setChecked(preference, false, false);
        }
    }

    private void connect() {
        Engine.instance().startServices(); // internally this is an async call in libtorrent
        updateConnectSwitchStatus();
    }

    private void disconnect() {
        Engine.instance().stopServices(true); // internally this is an async call in libtorrent
        updateConnectSwitchStatus();
    }

    private void updateStorageOptionSummary(String newPath) {
        // intentional repetition of preference value here
        String lollipopKey = "frostwire.prefs.storage.path_asf";
        if (AndroidPlatform.saf()) {
            Preference p = findPreference(lollipopKey);
            if (p != null) {
                p.setSummary(newPath);
            }
        }
    }
}
