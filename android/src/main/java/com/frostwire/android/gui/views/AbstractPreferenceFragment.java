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

package com.frostwire.android.gui.views;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import com.frostwire.android.core.FrostwirePreferenceDataStore;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractPreferenceFragment extends PreferenceFragmentCompat {

    protected static final String DIALOG_FRAGMENT_TAG =
            "androidx.preference.PreferenceFragment.DIALOG";

    private final int preferencesResId;

    public AbstractPreferenceFragment(int preferencesResId) {
        if (preferencesResId <= 0) {
            throw new IllegalArgumentException("AbstractPreferenceFragment: Invalid preferences resource ID");
        }
        this.preferencesResId = preferencesResId;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setPreferenceDataStore(new FrostwirePreferenceDataStore());
        addPreferencesFromResource(preferencesResId);
        initComponents();
    }

    // onAttach no longer needed for setTargetFragment — PreferenceDialogFragment.getPreference()
    // now resolves the parent fragment via getParentFragmentManager() instead.

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
            extends androidx.preference.PreferenceDialogFragmentCompat {

        /**
         * Returns the {@link DialogPreference} associated with this dialog by locating the
         * parent {@link PreferenceFragmentCompat} via {@link #getParentFragmentManager()} instead
         * of the deprecated {@link Fragment#getTargetFragment()}.
         */
        @Override
        public DialogPreference getPreference() {
            final Bundle args = getArguments();
            if (args == null) {
                throw new IllegalStateException("PreferenceDialogFragment: missing arguments");
            }
            final String key = args.getString(ARG_KEY);
            // Walk the fragment manager to find the parent PreferenceFragmentCompat.
            List<Fragment> fragments = getParentFragmentManager().getFragments();
            for (Fragment f : fragments) {
                if (f instanceof PreferenceFragmentCompat) {
                    DialogPreference pref = ((PreferenceFragmentCompat) f).findPreference(key);
                    if (pref != null) {
                        return pref;
                    }
                }
            }
            throw new IllegalStateException(
                    "PreferenceDialogFragment: could not find preference with key=" + key);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = requireContext();
            DialogPreference preference = getPreference();

            // initialize private super.mWhichButtonClicked
            onClick(null, DialogInterface.BUTTON_NEGATIVE);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            CharSequence dialogTitle = preference.getDialogTitle();
            if (dialogTitle != null) {
                builder.setTitle(dialogTitle);
            }

            if (preference.getDialogIcon() != null) {
                builder.setIcon(preference.getDialogIcon());
            }

            CharSequence positiveText = preference.getPositiveButtonText();
            if (positiveText == null) {
                positiveText = getString(android.R.string.ok);
            }

            CharSequence negativeText = preference.getNegativeButtonText();
            if (negativeText == null) {
                negativeText = getString(android.R.string.cancel);
            }

            builder.setPositiveButton(positiveText, this)
                    .setNegativeButton(negativeText, this);

            View contentView = onCreateDialogView(context);
            if (contentView != null) {
                onBindDialogView(contentView);
                builder.setView(contentView);
            } else {
                CharSequence dialogMessage = preference.getDialogMessage();
                if (dialogMessage != null) {
                    builder.setMessage(dialogMessage);
                }
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
        protected final void onPrepareDialogBuilder(@NonNull android.app.AlertDialog.Builder builder) {
        }
    }
}
