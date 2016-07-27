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

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.MediaStore.Audio;
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

    SetAsRingtoneMenuAction(final Context context, FileDescriptor fd, final DangerousPermissionsChecker writeSettingsPermissionChecker) {
        super(context, R.drawable.contextmenu_icon_ringtone, R.string.context_menu_use_as_ringtone);
        this.fd = fd;
        this.writeSettingsPermissionChecker = writeSettingsPermissionChecker;
    }

    @Override
    protected void onClick(Context context) {
        if (DangerousPermissionsChecker.hasPermissionToWriteSettings(context)) {
            setNewRingtone(context);
        } else {
            DangerousPermissionsChecker.requestPermissionToWriteSettings(writeSettingsPermissionChecker,
                    () -> {
                        // This callback is executed by (MainActivity|AudioPlayerActivity).onActivityResult(requestCode=DangerousPermissionChecker.WRITE_SETTINGS_PERMISSIONS_REQUEST_CODE,...)
                        // when the new System's Write Settings activity is finished and the permissions
                        // have been granted by the user.
                        if (context != null) {
                            setNewRingtone(context);
                        }
                    });
        }
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