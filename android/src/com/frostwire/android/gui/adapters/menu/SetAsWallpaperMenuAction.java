/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public class SetAsWallpaperMenuAction extends MenuAction {

    private final FWFileDescriptor fd;

    public SetAsWallpaperMenuAction(Context context, FWFileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_picture, R.string.set_as_wallpaper);
        this.fd = fd;
    }

    @Override
    public void onClick(final Context context) {
        if (fd.fileType != Constants.FILE_TYPE_PICTURES) {
            return;
        }
        UIUtils.showShortMessage(context, R.string.your_android_wall_paper_will_change);
        async(context, (c, path) -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                WallpaperManager.getInstance(c).setBitmap(bitmap);
            } catch (Throwable e) {
                UIUtils.showShortMessage(c, R.string.failed_to_set_wallpaper);
            }
        }, fd.filePath);
    }
}
