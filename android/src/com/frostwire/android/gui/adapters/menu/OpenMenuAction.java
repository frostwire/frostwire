/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class OpenMenuAction extends MenuAction {

    private final String path;
    private final String mime;

    public OpenMenuAction(Context context, String title, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_play, R.string.open_menu_action, title);
        this.path = path;
        this.mime = mime;
    }

    public OpenMenuAction(Context context, String path, String mime) {
        super(context, R.drawable.contextmenu_icon_play, R.string.open);
        this.path = path;
        this.mime = mime;
    }

    
    @Override
    protected void onClick(Context context) {
        UIUtils.openFile(context, path, mime);
    }
}
