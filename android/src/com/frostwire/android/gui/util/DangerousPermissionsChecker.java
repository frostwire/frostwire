/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * @author gubatron
 * @author aldenml
 */
public final class DangerousPermissionsChecker implements ActivityCompat.OnRequestPermissionsResultCallback {

    public boolean hasAskedBefore() {
        return requestCode == ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE && ConfigurationManager.instance().getBoolean(Constants.ASKED_FOR_ACCESS_COARSE_LOCATION_PERMISSIONS);
    }

    private static final Logger LOG = Logger.getLogger(DangerousPermissionsChecker.class);

    /**
     * Asks for both READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
     */
    public static final int EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE = 0x000A;
    public static final int ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE = 0x000B;

    // HACK: just couldn't find another way, and this saved a lot of overcomplicated logic in the onActivityResult handling activities.
    static long AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
    static byte FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;

    private final WeakReference<Activity> activityRef;
    private final int requestCode;

    public DangerousPermissionsChecker(Activity activity, int requestCode) {
        if (activity instanceof ActivityCompat.OnRequestPermissionsResultCallback) {
            this.requestCode = requestCode;
            this.activityRef = Ref.weak(activity);
        } else {
            throw new IllegalArgumentException("The activity must implement ActivityCompat.OnRequestPermissionsResultCallback");
        }
    }

    public Activity getActivity() {
        if (Ref.alive(activityRef)) {
            return activityRef.get();
        }
        return null;
    }

    public void requestPermissions() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        Activity activity = activityRef.get();
        String[] permissions = null;
        switch (requestCode) {
            case EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE:
                if (SystemUtils.hasAndroid10OrNewer()) {
                    permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
                } else {
                    permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                }
                break;
            case ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE:
                permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
                break;
        }

        if (permissions != null) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE:
                onExternalStoragePermissionsResult(permissions, grantResults);
                break;
            case ACCESS_COARSE_LOCATION_PERMISSIONS_REQUEST_CODE:
                onAccessCoarseLocationPermissionsResult(permissions, grantResults);
            default:
                break;
        }
    }

    // EXTERNAL STORAGE PERMISSIONS

    public boolean noAccess() {
        // simplified until otherwise necessary.
        return requestCode == EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE && noExternalStorageAccess();
    }

    private boolean noExternalStorageAccess() {
        if (!Ref.alive(activityRef)) {
            return true;
        }
        Activity activity = activityRef.get();
        if (SystemUtils.hasAndroid10OrNewer()) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED;
    }

    public static boolean handleOnWriteSettingsActivityResult(Activity handlerActivity) {
        boolean hasWriteSettings = DangerousPermissionsChecker.hasPermissionToWriteSettings(handlerActivity);

        if (!hasWriteSettings) {
            LOG.warn("handleOnWriteSettingsActivityResult! had no permission to write settings");
            AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
            FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
            return false;
        }

        if (AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK == -1) {
            LOG.warn("handleOnWriteSettingsActivityResult! AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK not set");
            return false;
        }

        MusicUtils.setRingtone(handlerActivity, AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK, FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK);
        AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
        FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
        return true;
    }

    public static boolean hasPermissionToWriteSettings(Context context) {
        return DangerousPermissionsChecker.canWriteSettingsAPILevel24andUp(context);
    }

    private static boolean canWriteSettingsAPILevel24andUp(Context context) {
        //TODO: See what's up with this reflection hack, this smell like a bug waiting to happen if it's not happening already
        try {
            final Class<?> SystemClass = android.provider.Settings.System.class;
            final Method canWriteMethod = SystemClass.getMethod("canWrite", Context.class);
            Object boolResult = canWriteMethod.invoke(null, context);
            if (boolResult == null) {
                return false;
            }
            return (boolean) boolResult;
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
        return false;
    }

    private boolean onExternalStoragePermissionsResult(String[] permissions, int[] grantResults) {
        if (!Ref.alive(activityRef)) {
            return false;
        }
        final Activity activity = activityRef.get();
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setIcon(R.drawable.sd_card_notification);
                    builder.setTitle(R.string.why_we_need_storage_permissions);
                    builder.setMessage(R.string.why_we_need_storage_permissions_summary);
                    builder.setNegativeButton(R.string.exit, (dialog, which) -> shutdownFrostWire());
                    builder.setPositiveButton(R.string.request_again, (dialog, which) -> requestPermissions());
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                    return false;
                }
            }
        }


        LOG.info("onExternalStoragePermissionsResult() " + Manifest.permission.READ_EXTERNAL_STORAGE + " granted");
        return true;
    }

    private boolean onAccessCoarseLocationPermissionsResult(String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                LOG.info("ACCESS_COARSE_LOCATION permission granted? " + (grantResults[i] == PackageManager.PERMISSION_GRANTED));
                return grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }
        return false;
    }

    private void shutdownFrostWire() {
        if (!Ref.alive(activityRef)) {
            return;
        }
        final Activity activity = activityRef.get();
        activity.finish();
        Engine.instance().shutdown();
        MusicUtils.requestMusicPlaybackServiceShutdown(activity);
    }
}
