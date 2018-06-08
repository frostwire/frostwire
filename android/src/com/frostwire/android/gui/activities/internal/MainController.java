/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.activities.internal;

import android.app.Fragment;
import android.content.Intent;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.WizardActivity;
import com.frostwire.android.gui.fragments.MyFilesFragment;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.fragments.TransfersFragment.TransferStatus;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MainController {

    private final WeakReference<MainActivity> activityRef;

    public MainController(MainActivity activity) {
        activityRef = Ref.weak(activity);
    }

    public MainActivity getActivity() {
        if (!Ref.alive(activityRef)) {
            return null;
        }
        return activityRef.get();
    }

    public void switchFragment(int itemId) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        Fragment fragment = activity.getFragmentByNavMenuId(itemId);
        if (fragment != null) {
            activity.switchContent(fragment);
        }
    }

    public void showPreferences() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        Intent i = new Intent(activity, SettingsActivity.class);
        activity.startActivity(i);
    }

    public void launchMyMusic() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        Intent i = new Intent(activity, com.andrew.apollo.ui.activities.HomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
    }

    public void showTransfers(TransferStatus status) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        if (!(activity.getCurrentFragment() instanceof TransfersFragment)) {
            activity.runOnUiThread(() -> {
                TransfersFragment fragment = (TransfersFragment) activity.getFragmentByNavMenuId(R.id.menu_main_transfers);
                fragment.selectStatusTab(status);
                switchFragment(R.id.menu_main_transfers);
            });
        }
    }

    public void showMyFiles() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        if (!(activity.getCurrentFragment() instanceof MyFilesFragment)) {
            activity.runOnUiThread(() -> {
                MyFilesFragment fragment = (MyFilesFragment) activity.getFragmentByNavMenuId(R.id.menu_main_library);
                switchFragment(R.id.menu_main_library);
            });
        }
    }

    public void startWizardActivity() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        ConfigurationManager.instance().resetToDefaults();
        Intent i = new Intent(activity, WizardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(i);
    }

    public void showShutdownDialog() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        activity.showShutdownDialog();
    }

    public void syncNavigationMenu() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        activity.syncNavigationMenu();
    }

    public void setTitle(CharSequence title) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        activity.setTitle(title);
    }

    public Fragment getFragmentByNavMenuId(int itemId) {
        if (!Ref.alive(activityRef)) {
            return null;
        }
        MainActivity activity = activityRef.get();
        return activity.getFragmentByNavMenuId(itemId);
    }

    public void switchContent(Fragment fragment) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        activity.switchContent(fragment);
    }
}
