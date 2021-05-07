/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.providers.TableFetchers;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class SendFileMenuAction extends MenuAction {

    private static final String TAG = "FW.SendFileMenuAction";

    private final FWFileDescriptor fd;

    public SendFileMenuAction(Context context, FWFileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_send, R.string.share);

        this.fd = fd;
    }

    @Override
    public void onClick(Context context) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(fd.mime);
            i.putExtra(Intent.EXTRA_SUBJECT, fd.title);
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(TableFetchers.getFetcher(fd.fileType).getExternalContentUri() + "/" + fd.id));
            context.startActivity(Intent.createChooser(i, context.getString(R.string.send_file_using)));
        } catch (Throwable e) {
            // catch for general android errors, in particular: android.content.ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.CHOOSER (has extras)
            Log.e(TAG, "Error in android framework", e);
        }
    }
}
