/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.util.Logger;

public final class HomeActivity extends BaseActivity {

    @SuppressLint("StaticFieldLeak")
    private static HomeActivity instance;

    private static final Logger LOG = Logger.getLogger(HomeActivity.class);
    private final DangerousPermissionsChecker<HomeActivity> notificationsPermissionsChecker;

    private boolean hasCheckedPermissions = false; // Single flag to prevent redundant checks

    public HomeActivity() {
        super(R.layout.activity_base);
        instance = this;
        notificationsPermissionsChecker = new DangerousPermissionsChecker<>(this, DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE);

    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        // Load the main fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.activity_base_content, new MusicBrowserPhoneFragment())
                    .commit();
        }

        // Add back pressed handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                HomeActivity.this.handleOnBackPressed();
            }
        });

        // Check permissions once
        if (!hasCheckedPermissions) {
            hasCheckedPermissions = true;
            requestForPostNotificationsPermission();
        }
    }

    public void requestForPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                LOG.info("Requesting POST_NOTIFICATIONS permission...");
                notificationsPermissionsChecker.requestPermissions();
            } else {
                LOG.info("POST_NOTIFICATIONS permission already granted.");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == DangerousPermissionsChecker.POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE) {
            LOG.info("HomeActivity::onRequestPermissionsResult() invoked with requestCode=" + requestCode);

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LOG.info("POST_NOTIFICATIONS permission granted.");
            } else {
                LOG.warn("POST_NOTIFICATIONS permission denied.");
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showPostNotificationsRationaleDialog();
                } else {
                    showSettingsRedirectDialog();
                }
            }
        }
    }

    private void showPostNotificationsRationaleDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Notification Permission Required")
                .setMessage("We need permission to show notifications about the music player status. "
                        + "Without this, Android may kill the app when it runs in the background.")
                .setPositiveButton("Allow", (dialog, which) -> {
                    LOG.info("User agreed to allow POST_NOTIFICATIONS permission.");
                    notificationsPermissionsChecker.requestPermissions();
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    LOG.warn("POST_NOTIFICATIONS permission denied by user.");
                    UIUtils.showLongMessage(this, "Permission denied. The app may close in the background.");
                })
                .setCancelable(false)
                .show();
    }

    private void showSettingsRedirectDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("We need permission to show notifications about the music player status. "
                        + "Please enable the permission from App Settings to avoid app interruptions.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    LOG.warn("User declined to open App Settings.");
                    UIUtils.showLongMessage(this, "Notification permission denied. Some features may not work properly.");
                })
                .setCancelable(false)
                .show();
    }

    public void handleOnBackPressed() {
        if (MusicUtils.isPlaying()) {
            return;
        }
        Offers.showInterstitialOfferIfNecessary(
                this,
                Offers.PLACEMENT_INTERSTITIAL_MAIN,
                false,
                false,
                true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static HomeActivity instance() {
        return instance;
    }
}
