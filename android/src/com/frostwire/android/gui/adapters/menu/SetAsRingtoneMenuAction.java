/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.adapters.menu;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore.Audio;
import android.support.v4.app.ActivityCompat;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
class SetAsRingtoneMenuAction extends MenuAction {
    private final FileDescriptor fd;
    private final DangerousPermissionsChecker writeSettingsPermissionChecker;

    SetAsRingtoneMenuAction(final Context context, FileDescriptor fd, DangerousPermissionsChecker writeSettingsPermissionChecker) {
        super(context, R.drawable.contextmenu_icon_ringtone, R.string.context_menu_use_as_ringtone);
        this.fd = fd;
        this.writeSettingsPermissionChecker = writeSettingsPermissionChecker;
    }

    @Override
    protected void onClick(Context context) {
        if (checkForPermissionToWriteSettings(context)) {
            setNewRingtone(context);
        } else {
            requestPermissionToWriteSettings(context);
        }
    }

    private boolean checkForPermissionToWriteSettings(Context context) {
        return (Build.VERSION.SDK_INT >= 23) ?
                DangerousPermissionsChecker.canWriteSettingsAPILevel23(context) :
                ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }



    private void requestPermissionToWriteSettings(final Context context) {
        // This callback is executed by MainActivity.onActivityResult(requestCode=DangerousPermissionChecker.WRITE_SETTINGS_PERMISSIONS_REQUEST_CODE,...)
        // when the new System's Write Settings activity is finished and the permissions
        // have been granted by the user.
        writeSettingsPermissionChecker.
            setPermissionsGrantedCallback(() -> { if (context != null) { setNewRingtone(context); } });
        writeSettingsPermissionChecker.requestPermissions();
    }

    private void setNewRingtone(Context context) {
        String uri = null;

        if (fd.fileType == Constants.FILE_TYPE_RINGTONES) {
            uri = Audio.Media.INTERNAL_CONTENT_URI.toString() + "/" + fd.id;
        } else if (fd.fileType == Constants.FILE_TYPE_AUDIO) {
            uri = Audio.Media.EXTERNAL_CONTENT_URI.toString() + "/" + fd.id;
        }

        if (uri != null) {
            try {
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, Uri.parse(uri));
                final String message = context.getString(R.string.set_as_ringtone, fd.title);
                UIUtils.showLongMessage(context, message);
            } catch (Throwable t) {
                t.printStackTrace();
                UIUtils.showLongMessage(context, R.string.ringtone_not_set);
            }
        }
    }
}