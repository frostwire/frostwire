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

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.content.Context;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.util.DangerousPermissionsChecker;
import com.frostwire.android.gui.util.WriteSettingsPermissionActivityHelper;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SetAsRingtoneMenuAction extends MenuAction {
    private final FWFileDescriptor fd;

    public SetAsRingtoneMenuAction(final Context context, FWFileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_ringtone, R.string.context_menu_use_as_ringtone);
        this.fd = fd;
    }

    @Override
    public void onClick(Context context) {
        if (DangerousPermissionsChecker.hasPermissionToWriteSettings(context)) {
            MusicUtils.setRingtone(context, fd.id, fd.fileType);
        } else {
            WriteSettingsPermissionActivityHelper helper = new WriteSettingsPermissionActivityHelper((Activity) context);
            helper.onSetRingtoneOption((Activity) context, fd.id, fd.fileType);
        }
    }
}