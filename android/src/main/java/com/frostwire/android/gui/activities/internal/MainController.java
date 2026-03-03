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

package com.frostwire.android.gui.activities.internal;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.Fragment;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.WizardActivity;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.fragments.TransfersFragment.TransferStatus;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
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

    void openOSFileExplorer() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        Activity activity = activityRef.get();

        // Get the actual save location (configured or default)
        String downloadPath = ConfigurationManager.instance().getStoragePath();
        if (downloadPath == null || downloadPath.isEmpty()) {
            // Use default path if not configured
            downloadPath = com.frostwire.platform.Platforms.get().systemPaths().data().getAbsolutePath();
        }

        // Try to open the folder using file URI (works with most file managers)
        try {
            File folder = new File(downloadPath);
            if (folder.exists() && folder.isDirectory()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.fromFile(folder);
                intent.setData(uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                return;
            }
        } catch (Throwable e) {
            LOG.warn("Could not open folder with file URI: " + downloadPath, e);
        }

        // Fallback to system Downloads action
        Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (Throwable e) {
            String errorMessage = activity.getString(R.string.error_cannot_open_downloads_folder);
            UIUtils.showShortMessage(activity, errorMessage);
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
