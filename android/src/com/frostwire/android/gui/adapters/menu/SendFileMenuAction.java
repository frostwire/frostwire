/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.adapters.menu;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.ContextCompat; // Added import

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.providers.TableFetcher;
import com.frostwire.android.core.providers.TableFetchers;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class SendFileMenuAction extends MenuAction {

    private static final String TAG = "FW.SendFileMenuAction";

    private final FWFileDescriptor fd;

    public SendFileMenuAction(Context context, FWFileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_send, R.string.share, UIUtils.getAppIconPrimaryColor(context));
        this.fd = fd;
    }

    @Override
    public void onClick(Context context) {
        TableFetcher fetcher = TableFetchers.getFetcher(fd.fileType);
        if (fetcher == TableFetchers.UNKNOWN_TABLE_FETCHER) {
            UIUtils.showLongMessage(context, R.string.cant_open_file);
            return;
        }
        try {

            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(fd.mime);
            i.putExtra(Intent.EXTRA_SUBJECT, fd.title);
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(fetcher.getExternalContentUri() + "/" + fd.id));
            context.startActivity(Intent.createChooser(i, context.getString(R.string.send_file_using)));
        } catch (Throwable e) {
            // catch for general android errors, in particular: android.content.ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.CHOOSER (has extras)
            Log.e(TAG, "Error in android framework", e);
        }
    }
}
