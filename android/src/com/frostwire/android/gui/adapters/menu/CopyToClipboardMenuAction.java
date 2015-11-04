/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Jose Molina (@votaguz)
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 * @author votaguz
 */
public class CopyToClipboardMenuAction extends MenuAction {
    private final int messageId;
    private final Object data;

    public CopyToClipboardMenuAction(Context context, int drawable, int actionNameId,
                                     int messageId, Object data) {
        super(context, drawable, actionNameId);
        this.messageId = messageId;
        this.data = data;
    }

    @Override
    protected void onClick(Context context) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null && data != null) {
            ClipData clip = ClipData.newPlainText("data", this.data.toString());
            clipboard.setPrimaryClip(clip);
            UIUtils.showLongMessage(context, this.messageId);
        }
    }
}
