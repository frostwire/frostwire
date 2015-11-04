/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2013, FrostWire(R). All rights reserved.
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

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SetAsWallpaperMenuAction extends MenuAction {

    private final FileDescriptor fd;

    public SetAsWallpaperMenuAction(Context context, FileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_picture, R.string.set_as_wallpaper);

        this.fd = fd;
    }

    @Override
    protected void onClick(Context context) {
        if (fd.fileType != Constants.FILE_TYPE_PICTURES) {
            return;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeFile(fd.filePath);
            WallpaperManager.getInstance(context).setBitmap(bitmap);
        } catch (Throwable e) {
            UIUtils.showShortMessage(context, R.string.failed_to_set_wallpaper);
        }
    }
}
