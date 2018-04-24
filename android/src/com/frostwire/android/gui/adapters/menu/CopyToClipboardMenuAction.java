/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Jose Molina (@votaguz)
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.gui.views.TimerObserver;

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
    public void onClick(Context context) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        Object data = getData();

        if (clipboard != null && data != null) {
            ClipData clip = ClipData.newPlainText("data", data.toString());
            clipboard.setPrimaryClip(clip);
            UIUtils.showLongMessage(context, messageId);
        }
        if (context instanceof TimerObserver) {
            ((TimerObserver) context).onTime();
        }
    }

    protected Object getData() {
        return data;
    }
}
