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

package com.frostwire.android.gui.util;

import android.app.Activity;

import com.andrew.apollo.utils.MusicUtils;

/**
 * Created on 7/28/16
 * @author gubatron
 * @author aldenml
 */
public class WriteSettingsPermissionActivityHelper {
    private final DangerousPermissionsChecker writeSettingsPermissionChecker;

    public WriteSettingsPermissionActivityHelper(Activity activity) {
        writeSettingsPermissionChecker = new DangerousPermissionsChecker(activity, DangerousPermissionsChecker.WRITE_SETTINGS_PERMISSIONS_REQUEST_CODE);
    }

    public void onSetRingtoneOption(final Activity activity, long audioId, byte fileType) {
        if (DangerousPermissionsChecker.hasPermissionToWriteSettings(activity)) {
            MusicUtils.setRingtone(activity, audioId, fileType);
        } else {
            // HACK: found no other way to pass this back, tried sending it on the intent that opens the permission screen
            // but the intent that comes back doesn't keep the extra data in it.
            DangerousPermissionsChecker.AUDIO_ID_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = audioId;
            DangerousPermissionsChecker.FILE_TYPE_FOR_WRITE_SETTINGS_RINGTONE_CALLBACK = fileType;
            writeSettingsPermissionChecker.requestPermissions();
        }
    }

    public boolean onActivityResult(Activity activity, int requestCode) {
        return requestCode == DangerousPermissionsChecker.WRITE_SETTINGS_PERMISSIONS_REQUEST_CODE &&
                DangerousPermissionsChecker.handleOnWriteSettingsActivityResult(activity);
    }
}
