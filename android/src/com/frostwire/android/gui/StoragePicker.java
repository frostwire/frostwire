/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Emil Suleymanov (sssemil)
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

package com.frostwire.android.gui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.support.v4.provider.DocumentFile;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.logging.Logger;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class StoragePicker {

    private static final Logger LOG = Logger.getLogger(StoragePicker.class);

    public static final String ACTION_OPEN_DOCUMENT_TREE = "android.intent.action.OPEN_DOCUMENT_TREE";

    public static final int SELECT_FOLDER_REQUEST_CODE = 1267123;

    private static final String EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED";

    private static final String PRIMARY_VOLUME_NAME = "primary";

    private static final String PATH_TREE = "tree";

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

                Method takePersistableUriPermissionM = cr.getClass().getMethod("takePersistableUriPermission", Uri.class, int.class);
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                takePersistableUriPermissionM.invoke(cr, treeUri, takeFlags);

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
                        result = getFullPathFromTreeUri(context, treeUri);
                    }
                }
            }
        } catch (Throwable e) {
            UIUtils.showShortMessage(context, R.string.storage_picker_treeuri_error);
            LOG.error("Error handling folder selection", e);
            result = null;
        }

        return result;
    }

    private static String getFullPathFromTreeUri(Context ctx, final Uri treeUri) {
        if (treeUri == null) {
            return null;
        }

        StorageManager mStorageManager = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);

        String volumePath = getVolumePath(mStorageManager, getVolumeIdFromTreeUri(treeUri));
        if (volumePath == null) {
            return File.separator;
        }
        if (volumePath.endsWith(File.separator)) {
            volumePath = volumePath.substring(0, volumePath.length() - 1);
        }

        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator)) {
            documentPath = documentPath.substring(0, documentPath.length() - 1);
        }

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator)) {
                return volumePath + documentPath;
            } else {
                return volumePath + File.separator + documentPath;
            }
        } else {
            return volumePath;
        }
    }

    private static String getVolumePath(StorageManager mStorageManager, final String volumeId) {
        try {
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");

            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                // primary volume?
                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId)) {
                    return (String) getPath.invoke(storageVolumeElement);
                }

                // other volumes?
                if (uuid != null) {
                    if (uuid.equals(volumeId)) {
                        return (String) getPath.invoke(storageVolumeElement);
                    }
                }
            }

            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");

        if (split.length > 0) {
            return split[0];
        } else {
            return null;
        }
    }

    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) {
            return split[1];
        } else {
            return File.separator;
        }
    }

    /**
     * Extract the via {@link Document#COLUMN_DOCUMENT_ID} from the given URI.
     */
    public static String getTreeDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
            return paths.get(1);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }
}
