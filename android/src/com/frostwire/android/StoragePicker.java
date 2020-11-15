/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Emil Suleymanov (sssemil)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

import androidx.documentfile.provider.DocumentFile;

/**
 * @author gubatron
 * @author aldenml
 */
public final class StoragePicker {

    private static final Logger LOG = Logger.getLogger(StoragePicker.class);

    public static final String ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE";

    public static final int SELECT_FOLDER_REQUEST_CODE = 126;

    private static final String EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED";

    private StoragePicker() {
    }

    public static void show(Activity activity) {
        Intent intent = new Intent(ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra(EXTRA_SHOW_ADVANCED, true);
        activity.startActivityForResult(intent, SELECT_FOLDER_REQUEST_CODE);
    }

    public static String handle(Context context, int requestCode, int resultCode, Intent data) {
        String result = null;
        try {

            if (resultCode == Activity.RESULT_OK && requestCode == SELECT_FOLDER_REQUEST_CODE) {
                Uri treeUri = data.getData();

                ContentResolver cr = context.getContentResolver();

                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cr.takePersistableUriPermission(treeUri, takeFlags);

                if (treeUri == null) {
                    UIUtils.showShortMessage(context, R.string.storage_picker_treeuri_null);
                    result = null;
                } else {
                    DocumentFile file = DocumentFile.fromTreeUri(context, treeUri);
                    if (!file.isDirectory()) {
                        UIUtils.showShortMessage(context, R.string.storage_picker_treeuri_not_directory);
                        result = null;
                    } else if (!file.canWrite()) {
                        UIUtils.showShortMessage(context, R.string.storage_picker_treeuri_cant_write);
                        result = null;
                    } else {
                        LollipopFileSystem fs = (LollipopFileSystem) Platforms.fileSystem();
                        result = fs.getTreePath(treeUri);
                        if (result != null && !result.endsWith("/FrostWire")) {
                            DocumentFile f = file.findFile("FrostWire");
                            if (f == null) {
                                file.createDirectory("FrostWire");
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            UIUtils.showShortMessage(context, R.string.storage_picker_treeuri_error);
            LOG.error("Error handling folder selection", e);
            result = null;
        }

        if (result != null) {
            ConfigurationManager.instance().setStoragePath(result);
            BTEngine.ctx.dataDir = Platforms.data();
            BTEngine.ctx.torrentsDir = Platforms.torrents();
        }

        return result;
    }
}
