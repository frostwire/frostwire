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

package com.frostwire.android.gui.activities;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.util.Log;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity2;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SettingsActivity2 extends AbstractActivity2
        implements PreferenceFragment.OnPreferenceStartFragmentCallback {

    /**
     * When starting this activity, the invoking Intent can contain this extra
     * string to specify which fragment should be initially displayed.
     */
    public static final String EXTRA_SHOW_FRAGMENT =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT;

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specified to supply a Bundle of arguments to pass
     * to that fragment when it is instantiated during the initial creation
     * of the activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS;

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specify to supply the title to be shown for
     * that fragment.
     */
    public static final String EXTRA_SHOW_FRAGMENT_TITLE =
            PreferenceActivity.EXTRA_SHOW_FRAGMENT_TITLE;

    // keep this field as a starting point to support multipane settings in tablet
    // see PreferenceFragment source code
    private final boolean singlePane;

    public SettingsActivity2() {
        super(R.layout.activity_settings);
        singlePane = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String fragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        Bundle fragmentArgs = intent.getBundleExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS);
        int fragmentTitle = intent.getIntExtra(EXTRA_SHOW_FRAGMENT_TITLE, 0);

        if (fragmentName == null) {
            fragmentName = SettingsActivity2.Application.class.getName();
        }

        switchToFragment(fragmentName, fragmentArgs, fragmentTitle);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        startPreferencePanel(pref.getFragment(), pref.getExtras(), pref.getTitleRes(), null, 0);
        return true;
    }

    private void startPreferencePanel(String fragmentClass, Bundle args, int titleRes,
                                      Fragment resultTo, int resultRequestCode) {
        if (singlePane) {
            startWithFragment(fragmentClass, args, titleRes, resultTo, resultRequestCode);
        } else {
            // check singlePane comment
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private void startWithFragment(String fragmentName, Bundle args, int titleRes,
                                   Fragment resultTo, int resultRequestCode) {
        Intent intent = buildStartFragmentIntent(fragmentName, args, titleRes);
        if (resultTo == null) {
            startActivity(intent);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    private Intent buildStartFragmentIntent(String fragmentName, Bundle args, int titleRes) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, titleRes);
        return intent;
    }

    private void switchToFragment(String fragmentName, Bundle args, int titleRes) {
        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.replace(R.id.activity_settings_content, f);
        transaction.commitAllowingStateLoss();

        if (titleRes != 0) {
            setTitle(titleRes);
        }
    }

    public static class Application extends PreferenceFragment {

        SwitchPreference connectSwitch;
        SwitchPreference wifiOnlySwitch;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_application);
        }

        @Override
        public void onResume() {
            super.onResume();
            setupConnectSwitch();
            setupWiFiExclusiveSwitch();
        }

        private void setupWiFiExclusiveSwitch() {
            wifiOnlySwitch = (SwitchPreference) findPreference(Constants.PREF_KEY_INTERNAL_CONNECT_DISCONNECT);
            if (wifiOnlySwitch != null) {
                wifiOnlySwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        return false;
                        //todo stop transfers if not on wifi
                    }
                });
            }
        }

        private void setupConnectSwitch() {
            connectSwitch = (SwitchPreference) findPreference(Constants.PREF_KEY_INTERNAL_CONNECT_DISCONNECT);
            if (connectSwitch != null) {
                connectSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final boolean newStatus = (Boolean) newValue;
                        if (Engine.instance().isStarted() && !newStatus) {
                            changeConnectionState(false, R.string.toast_on_disconnect);
                        } else if (newStatus && (Engine.instance().isStopped() || Engine.instance().isDisconnected())) {
                            changeConnectionState(true, R.string.toast_on_connect);
                        }
                        return true;
                    }
                });
            }
            updateConnectSwitch();
        }

        private void changeConnectionState(final boolean newState, final int messageId) {
            final WeakReference<Activity> context = Ref.weak(getActivity());
            disableConnectSwitchWhileStateIsChanging();
            Runnable backgroundTask = new Runnable() {
                @Override
                public void run() {
                    if (newState) {
                        Engine.instance().startServices();
                    } else {
                        Engine.instance().stopServices(true);
                    }
                    Runnable post = new Runnable() {
                        @Override
                        public void run() {
                            UIUtils.showShortMessage(context.get(), messageId);
                            updateConnectSwitch();
                        }
                    };
                    if (Ref.alive(context)) {
                        context.get().runOnUiThread(post);
                    }
                }
            };
            Engine.instance().getThreadPool().submit(backgroundTask);
        }

        private void updateConnectSwitch() {
            if (connectSwitch != null) {
                final Preference.OnPreferenceChangeListener onPreferenceChangeListener = connectSwitch.getOnPreferenceChangeListener();
                connectSwitch.setOnPreferenceChangeListener(null);
                connectSwitch.setSummary(R.string.bittorrent_network_summary);
                connectSwitch.setEnabled(true);
                if (Engine.instance().isStarted()) {
                    connectSwitch.setChecked(true);
                    connectSwitch.setSummaryOn(R.string.connect); //todo proper string
                } else if (Engine.instance().isStarting() || Engine.instance().isStopping()) {
                    disableConnectSwitchWhileStateIsChanging();
                } else if (Engine.instance().isStopped() || Engine.instance().isDisconnected()) {
                    connectSwitch.setChecked(false);
                    connectSwitch.setSummaryOff(R.string.disconnected); //todo proper string
                }
                connectSwitch.setOnPreferenceChangeListener(onPreferenceChangeListener);
            }
        }

        private void disableConnectSwitchWhileStateIsChanging() {
            Log.w("P", "disable");
            final Preference.OnPreferenceChangeListener onPreferenceChangeListener = connectSwitch.getOnPreferenceChangeListener();
            connectSwitch.setOnPreferenceChangeListener(null);
            connectSwitch.setEnabled(false);
            connectSwitch.setSummaryOff(R.string.im_on_it);
            connectSwitch.setSummaryOn(R.string.im_on_it);
            connectSwitch.setOnPreferenceChangeListener(onPreferenceChangeListener);
        }

    }

    public static class Search extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_search);
        }
    }

    public static class Torrent extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_torrent);
        }
    }

    public static class Other extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.settings_other);
        }
    }
}
