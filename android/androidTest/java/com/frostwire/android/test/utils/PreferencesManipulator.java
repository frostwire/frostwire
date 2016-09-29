package com.frostwire.android.test.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.frostwire.android.core.Constants;

import java.util.Map;

public class PreferencesManipulator {
    SharedPreferences mPreferences;

    public PreferencesManipulator(Context context) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void clearGuiPrefs() {
        clearPrefsWithPrefix("frostwire.prefs.gui.");
    }

    public void userHasStartedAppOnce() {
        mPreferences.edit()
                .putBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED, true)
                .putBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE, true)
                .putBoolean(Constants.PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET, true)
                .commit();
    }

    private void clearPrefsWithPrefix(String prefix) {
        SharedPreferences.Editor editor = mPreferences.edit();
        Map<String, ?> allPrefs = mPreferences.getAll();
        for (String key : allPrefs.keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.commit();
    }


}
