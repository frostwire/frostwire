/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Jose Molina (@votaguz), Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters.menu;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.util.Logger;

/**
 * @author gubatron
 * @author aldenml
 * @author votaguz
 * @author marcelinkaaa
 */
public class CopyToClipboardMenuAction extends MenuAction {

    private final int messageId;
    private final Object data;

    private static final Logger LOG = Logger.getLogger(CopyToClipboardMenuAction.class);

    public CopyToClipboardMenuAction(Context context, int drawable, int actionNameId,
                                     int messageId, Object data) {
        super(context, drawable, actionNameId, UIUtils.getAppIconPrimaryColor(context));
        this.messageId = messageId;
        this.data = data;
    }

    @Override
    public void onClick(Context context) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        Object data = getData();

        if (clipboard != null && data != null) {
            try {
                ClipData clip = ClipData.newPlainText("data", data.toString());
                clipboard.setPrimaryClip(clip);
                UIUtils.showLongMessage(context, messageId);
            } catch (SecurityException e) {
                LOG.error("Error copying to clipboard", e);
            }
        }
        if (context instanceof TimerObserver) {
            ((TimerObserver) context).onTime();
        }
    }

    protected Object getData() {
        return data;
    }
}
