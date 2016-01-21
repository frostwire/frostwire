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

package com.frostwire.android;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import com.frostwire.logging.Logger;
import com.frostwire.platform.FileSystem;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LollipopFileSystem implements FileSystem {

    private static final Logger LOG = Logger.getLogger(LollipopFileSystem.class);

    private final Application app;

    public LollipopFileSystem(Application app) {
        this.app = app;
    }

    @Override
    public boolean rename(File src, File dest) {
        if (src.renameTo(dest)) { // old way
            return true;
        }

        DocumentFile srcF = getFile(app, src, false);
        DocumentFile destF = getFile(app, dest, true);

        if (srcF == null) {
            LOG.error("Unable to obtain document for file: " + src);
        }

        if (destF == null) {
            LOG.error("Unable to obtain or create document for file: " + dest);
        }

        return copy(app, srcF, destF);
    }

    private static DocumentFile getDirectory(Context context, File dir, boolean create) {
        String baseFolder = getExtSdCardFolder(context, dir);
        if (baseFolder == null) {
            if (create) {
                return dir.mkdirs() ? DocumentFile.fromFile(dir) : null;
            } else {
                return dir.isDirectory() ? DocumentFile.fromFile(dir) : null;
            }
        }

        String fullPath = dir.getAbsolutePath();
        String relativePath = baseFolder.length() < fullPath.length() ? fullPath.substring(baseFolder.length() + 1) : "";

        String[] segments = relativePath.split("/");

        Uri rootUri = getDocumentUri(context, new File(baseFolder));
        DocumentFile f = DocumentFile.fromTreeUri(context, rootUri);

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            DocumentFile child = f.findFile(segment);
            if (child != null) {
                f = child;
            } else {
                if (create) {
                    f = f.createDirectory(segment);
                    if (f == null) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }

        return f.isDirectory() ? f : null;
    }

    private static DocumentFile getFile(Context context, File file, boolean create) {
        File parent = file.getParentFile();
        if (parent == null) {
            return DocumentFile.fromFile(file);
        }
        DocumentFile f = getDirectory(context, parent, true);
        if (f != null) {
            String name = file.getName();
            DocumentFile child = f.findFile(name);
            if (child != null) {
                if (child.isFile()) {
                    f = child;
                } else {
                    f = null;
                }
            } else {
                if (create) {
                    f = f.createFile("application/octet-stream", name);
                } else {
                    f = null;
                }
            }
        }

        return f;
    }

    public static Uri getDocumentUri(Context context, File file) {
        String baseFolder = getExtSdCardFolder(context, file);
        if (baseFolder == null) {
            return null;
        }

        String volumeId = getVolumeId(context, baseFolder);
        if (volumeId == null) {
            return null;
        }

        String fullPath = file.getAbsolutePath();
        String relativePath = baseFolder.length() < fullPath.length() ? fullPath.substring(baseFolder.length() + 1) : "";

        relativePath = relativePath.replace("/", "%2F");
        String uri = "content://com.android.externalstorage.documents/tree/" + volumeId + "%3A" + relativePath;

        return Uri.parse(uri);
    }

    public static String getPath(Context context, Uri treeUri) {
        if (treeUri == null) {
            return null;
        }

        StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

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

        String path = volumePath;

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator)) {
                path = volumePath + documentPath;
            } else {
                path = volumePath + File.separator + documentPath;
            }
        }

        return path;
    }

    private static String getExtSdCardFolder(Context context, File file) {
        String[] extSdPaths = getExtSdCardPaths(context);
        try {
            for (int i = 0; i < extSdPaths.length; i++) {
                if (file.getCanonicalPath().startsWith(extSdPaths[i])) {
                    return extSdPaths[i];
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String[] getExtSdCardPaths(Context context) {
        List<String> paths = new ArrayList<>();
        for (File file : ContextCompat.getExternalFilesDirs(context, "external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index >= 0) {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                } else {
                    LOG.warn("ext sd card path wrong: " + file.getAbsolutePath());
                }
            }
        }
        if (paths.isEmpty()) paths.add("/storage/sdcard1");
        return paths.toArray(new String[0]);
    }

    private static String getVolumeId(Context context, final String volumePath) {
        try {
            StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);

            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");

            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);

                if (path != null) {
                    if (path.equals(volumePath)) {
                        return (String) getUuid.invoke(storageVolumeElement);
                    }
                }
            }

            // not found.
            return null;
        } catch (Exception ex) {
            return null;
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
                if (primary && "primary".equals(volumeId)) {
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

    private static String getTreeDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && "tree".equals(paths.get(0))) {
            return paths.get(1);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    //------------ more tools methods

    private static boolean copy(Context context, DocumentFile source, DocumentFile target) {
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = context.getContentResolver().openInputStream(source.getUri());
            outStream = context.getContentResolver().openOutputStream(target.getUri());

            byte[] buffer = new byte[16384]; // MAGIC_NUMBER
            int bytesRead;
            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        } catch (Throwable e) {
            LOG.error("Error when copying file from " + source.getUri() + " to " + target.getUri(), e);
            return false;
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);
        }

        return true;
    }
}
