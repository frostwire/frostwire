/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.Toast;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.WizardActivity;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.fragments.TransfersFragment.TransferStatus;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MainController {
    private final static Logger LOG = Logger.getLogger(MainController.class);
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

    void showPreferences() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        Intent i = new Intent(activity, SettingsActivity.class);
        activity.startActivity(i);
    }

    void launchMyMusic() {
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
                try {
                    TransfersFragment fragment = (TransfersFragment) activity.getFragmentByNavMenuId(R.id.menu_main_transfers);
                    fragment.selectStatusTab(status);
                    switchFragment(R.id.menu_main_transfers);
                    fragment.onTime();
                } catch (Throwable t) {
                    if (BuildConfig.DEBUG) {
                        throw t;
                    }
                    LOG.error("showTransfers() " + t.getMessage(), t);
                }

            });
        }
    }

    void showMyFiles() {
        if (!Ref.alive(activityRef)) {
            return;
        }

        /*
         Downloads/FrostWire
         (used to be Downloads/FrostWire/TorrentData)
         */
        File data = Platforms.get().systemPaths().data();

        Uri downloadsFolderUri = Uri.parse(data.getAbsolutePath());

        // This opens com.google.android.apps.docs but not on the given folder
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setDataAndType(downloadsFolderUri, "*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra("org.openintents.extra.ABSOLUTE_PATH", data.getAbsolutePath());

        ActivityInfo resolvedActivityInfo = intent.resolveActivityInfo(activityRef.get().getPackageManager(), 0);
        if (resolvedActivityInfo != null) {
            LOG.info("showMyFiles: resolved package name=" + resolvedActivityInfo.packageName);
            LOG.info("showMyFiles: uri=" + downloadsFolderUri);
            activityRef.get().startActivity(intent);
        } else {
            UIUtils.showToastMessage(activityRef.get(),"Could not find a file explorer", Toast.LENGTH_SHORT);
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

    void showShutdownDialog() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        activity.showShutdownDialog();
    }

    void syncNavigationMenu() {
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

    Fragment getFragmentByNavMenuId(int itemId) {
        if (!Ref.alive(activityRef)) {
            return null;
        }
        MainActivity activity = activityRef.get();
        return activity.getFragmentByNavMenuId(itemId);
    }

    void switchContent(Fragment fragment) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        MainActivity activity = activityRef.get();
        activity.switchContent(fragment);
    }
}
