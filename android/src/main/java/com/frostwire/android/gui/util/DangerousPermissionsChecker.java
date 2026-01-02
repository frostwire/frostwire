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

package com.frostwire.android.gui.util;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * @author gubatron
 * @author aldenml
 */
public final class DangerousPermissionsChecker<T extends ActivityCompat.OnRequestPermissionsResultCallback> implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final Logger LOG = Logger.getLogger(DangerousPermissionsChecker.class);

    /**
     * Asks for both READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
     */
    public static final int EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE = 0x000A; // 10

    public static final int POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE = 0x000B; // 11

    // HACK: just couldn't find another way, and this saved a lot of overcomplicated logic in the onActivityResult handling activities.
    static long AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;
    static byte FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = -1;

    private final WeakReference<Activity> activityRef;
    private final int requestCode;

    public DangerousPermissionsChecker(T activity, int requestCode) {
        if (activity != null) {
            this.requestCode = requestCode;
            this.activityRef = Ref.weak((Activity) activity);
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
        if (requestCode == EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE) {
            if (SystemUtils.hasAndroid13OrNewer()) {
                // As of Android13 the geniuses at Android decided yet another change
                // on how to ask for permissions, now we have to be more granular about it
                permissions = new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES};
            } else if (SystemUtils.hasAndroid11OrNewer()) {
                // no more need for WRITE_EXTERNAL_STORAGE permission on Android 11,
                // android:requestLegacyExternalStorage does nothing for android11 and up
                // and it's ok because they finally let you use File API on the public downloads folders
                permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
            } else {
                // Android 10 (29) + android:requestLegacyExternalStorage should make it work
                permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            }
        } else if (requestCode == POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE) {
            permissions = new String[]{Manifest.permission.POST_NOTIFICATIONS};
        }

        if (permissions != null) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE) {
            boolean externalStorageGranted = onExternalStoragePermissionsResult(permissions, grantResults);
            if (externalStorageGranted) {
                // if there are playlists with no owner, we fix them by recreating them and copying all their songs
                // to properly created playlists
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> MusicUtils.fixPlaylistsOwnership());
            }
        } else if (requestCode == POST_NOTIFICATIONS_PERMISSIONS_REQUEST_CODE) {
            // do nothing for now
            LOG.info("DangerousPermissionsChecker.onRequestPermissionsResult() requestCode=" + requestCode);
            onPostNotificationsPermissionsResult(permissions, grantResults);
        }
    }

    /**
     * If Post notification permissions are granted, we start the MusicPlaybackService
     */
    private void onPostNotificationsPermissionsResult(String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED && permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                LOG.info("onPostNotificationsPermissionsResult() " + Manifest.permission.POST_NOTIFICATIONS + " granted");
                MusicPlaybackService.onCreateSafe();
            }
        }
    }

    // EXTERNAL STORAGE PERMISSIONS

    public boolean noExternalStorageAccess() {
        // simplified until otherwise necessary.
        return requestCode == EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE && noExternalStorageAccessInternal();
    }

    private boolean noExternalStorageAccessInternal() {
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
                    AlertDialog alertDialog = getAlertDialogStoragePermissionsRationale(activity);
                    alertDialog.show();
                    return false;
                }
            }
        }

        LOG.info("onExternalStoragePermissionsResult() " + Manifest.permission.READ_EXTERNAL_STORAGE + " granted");
        return true;
    }

    private AlertDialog getAlertDialogStoragePermissionsRationale(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setIcon(R.drawable.sd_card_notification);
        builder.setTitle(R.string.why_we_need_storage_permissions);
        builder.setMessage(R.string.why_we_need_storage_permissions_summary);
        builder.setNegativeButton(R.string.exit, (dialog, which) -> shutdownFrostWire());
        builder.setPositiveButton(R.string.request_again, (dialog, which) -> requestPermissions());
        AlertDialog alertDialog = builder.create();
        return alertDialog;
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
