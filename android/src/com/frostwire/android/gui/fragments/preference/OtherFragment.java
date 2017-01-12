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

import android.app.NotificationManager;
import android.content.Context;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.view.View;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.services.EngineService;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.ButtonActionPreference2;
import com.frostwire.uxstats.UXStats;

/**
 * @author gubatron
 * @author aldenml
 */
public final class OtherFragment extends AbstractPreferenceFragment {

    public OtherFragment() {
        super(R.xml.settings_other);
    }

    @Override
    protected void initComponents() {
        setupPermanentStatusNotificationOption();
        setupHapticFeedback();
        setupUXStatsOption();
        setupClearIndex();
    }

    private void setupPermanentStatusNotificationOption() {
        CheckBoxPreference cb = findPreference(Constants.PREF_KEY_GUI_ENABLE_PERMANENT_STATUS_NOTIFICATION);
        if (cb != null) {
            cb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean notificationEnabled = (boolean) newValue;
                    if (!notificationEnabled) {
                        Context ctx = getActivity();
                        NotificationManager notificationService = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (notificationService != null) {
                            notificationService.cancel(EngineService.FROSTWIRE_STATUS_NOTIFICATION);
                        }
                    }
                    return true;
                }
            });
        }
    }

    private void setupHapticFeedback() {
        final CheckBoxPreference cb = findPreference(Constants.PREF_KEY_GUI_HAPTIC_FEEDBACK_ON);
        if (cb != null) {
            cb.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_HAPTIC_FEEDBACK_ON, cb.isChecked());
                    Engine.instance().getVibrator().onPreferenceChanged();
                    return true;
                }
            });
        }
    }

    private void setupUXStatsOption() {
        CheckBoxPreference checkPref = findPreference(Constants.PREF_KEY_UXSTATS_ENABLED);
        if (checkPref != null) {
            checkPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean newVal = (Boolean) newValue;
                    if (!newVal) { // not send ux stats
                        UXStats.instance().setContext(null);
                    }
                    return true;
                }
            });
        }
    }

    private void setupClearIndex() {
        final ButtonActionPreference2 preference = findPreference("frostwire.prefs.internal.clear_index");

        if (preference != null) {
            updateIndexSummary(preference);
            preference.setOnActionListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalSearchEngine.instance().clearCache();
                    UIUtils.showShortMessage(getActivity(), R.string.deleted_crawl_cache);
                    updateIndexSummary(preference);
                }
            });
        }
    }

    private void updateIndexSummary(Preference preference) {
        float size = (((float) LocalSearchEngine.instance().getCacheSize()) / 1024) / 1024;
        preference.setSummary(getString(R.string.crawl_cache_size, size));
    }
}
