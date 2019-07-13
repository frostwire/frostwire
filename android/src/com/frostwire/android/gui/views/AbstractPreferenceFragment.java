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

import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractPreferenceFragment extends PreferenceFragment {

    protected static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";

    private final int preferencesResId;

    public AbstractPreferenceFragment(int preferencesResId) {
        this.preferencesResId = preferencesResId;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(preferencesResId);
        initComponents();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // this is necessary to avoid a crash with double rotation of the screen
        Fragment f = getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG);
        if (f != null) {
            f.setTargetFragment(this, 0);
        }
    }

    protected void initComponents() {
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Preference> T findPreference(String key) {
        return (T) super.findPreference(key);
    }

    protected final void setEnabled(Preference preference, boolean enabled, boolean notifyChange) {
        if (notifyChange) {
            preference.setEnabled(enabled);
        } else {
            Preference.OnPreferenceChangeListener l = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener(null);
            preference.setEnabled(enabled);
            preference.setOnPreferenceChangeListener(l);
        }
    }

    protected final void setChecked(TwoStatePreference preference, boolean checked, boolean notifyChange) {
        if (notifyChange) {
            preference.setChecked(checked);
        } else {
            Preference.OnPreferenceChangeListener l = preference.getOnPreferenceChangeListener();
            preference.setOnPreferenceChangeListener(null);
            preference.setChecked(checked);
            preference.setOnPreferenceChangeListener(l);
        }
    }

    public static abstract class PreferenceDialogFragment
            extends androidx.preference.PreferenceDialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            // initialize private super.mWhichButtonClicked
            onClick(null, DialogInterface.BUTTON_NEGATIVE);

            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setIcon(this.getValue("mDialogIcon"))
                    .setPositiveButton(get("mPositiveButtonText"), this)
                    .setNegativeButton(get("mNegativeButtonText"), this);

            View contentView = onCreateDialogView(context);
            if (contentView != null) {
                onBindDialogView(contentView);
                builder.setView(contentView);
            } else {
                builder.setMessage(get("mDialogMessage"));
            }

            //onPrepareDialogBuilder(builder);

            Dialog dialog = builder.create();
            if (needInputMethod()) {
                Window window = dialog.getWindow();
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }

            return dialog;
        }

        // final to effectively hide it
        protected final void onPrepareDialogBuilder(android.app.AlertDialog.Builder builder) {
        }

//        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
//        }

        @SuppressWarnings("unchecked")
        private <T> T getValue(String name) {
            try {
                Field f = androidx.preference.PreferenceDialogFragment.class.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(this);
            } catch (Throwable e) {
                // ignore
            }
            return null;
        }

        private String get(String name) {
            return getValue(name);
        }
    }
}
