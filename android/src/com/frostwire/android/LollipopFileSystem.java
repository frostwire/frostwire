/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.util.LruCache;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.platform.FileFilter;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LollipopFileSystem implements FileSystem {

    private static final Logger LOG = Logger.getLogger(LollipopFileSystem.class);

    private static final int CACHE_MAX_SIZE = 1000;
    private static final LruCache<String, DocumentFile> CACHE = new LruCache<>(CACHE_MAX_SIZE);

    private static final List<String> FIXED_SDCARD_PATHS = buildFixedSdCardPaths();

    private final Application app;

    public LollipopFileSystem(Application app) {
        this.app = app;
    }

    @Override
    public boolean isDirectory(File file) {
        if (file.isDirectory()) {
            return true;
        }

        DocumentFile f = getDocument(app, file);

        return f != null && f.isDirectory();
    }

    @Override
    public boolean isFile(File file) {
        if (file.isFile()) {
            return true;
        }

        DocumentFile f = getDocument(app, file);

        return f != null && f.isFile();
    }

    @Override
    public boolean canRead(File file) {
        if (file.canRead()) {
            return true;
        }

        DocumentFile f = getDocument(app, file);

        return f != null && f.canRead();
    }

    @Override
    public boolean canWrite(File file) {
        if (file.canWrite()) {
            return true;
        }

        DocumentFile f = getDocument(app, file);

        return f != null && f.canWrite();
    }

    @Override
    public long length(File file) {
        long r = file.length();
        if (r > 0) {
            return r;
        }

        DocumentFile f = getDocument(app, file);

        return f != null ? f.length() : 0;
    }

    @Override
    public long lastModified(File file) {
        long r = file.lastModified();
        if (r > 0) {
            return r;
        }

        DocumentFile f = getDocument(app, file);

        return f != null ? f.lastModified() : 0;
    }

    @Override
    public boolean exists(File file) {
        if (file.exists()) {
            return true;
        }

        DocumentFile f = getDocument(app, file);

        return f != null && f.exists();
    }

    @Override
    public boolean mkdirs(File file) {
        if (file.mkdirs()) {
            return true;
        }

        DocumentFile f = getDirectory(app, file, false);
        if (f != null) {
            return false; // already exists
        }

        f = getDirectory(app, file, true);

        return f != null;
    }

    @Override
    public boolean delete(File file) {
        if (file.delete()) {
            return true;
        }

        DocumentFile f = getDocument(app, file);

        return f != null && f.delete();
    }

    @Override
    public File[] listFiles(File file, FileFilter filter) {
        try {
            File[] files = file.listFiles(filter);
            if (files != null) {
                return files;
            }
        } catch (Throwable e) {
            // ignore, try with SAF
        }

        LOG.warn("Using SAF for listFiles, could be a costly operation");

        DocumentFile f = getDirectory(app, file, false);
        if (f == null) {
            return null; // does not exists
        }

        DocumentFile[] files = f.listFiles();
        if (filter != null && files != null) {
            List<File> result = new ArrayList<>(files.length);
            for (DocumentFile file1 : files) {
                Uri uri = file1.getUri();
                String path = getDocumentPath(uri);
                if (path == null) {
                    continue;
                }
                File child = new File(path);
                if (filter.accept(child)) {
                    result.add(child);
                }
            }

            return result.toArray(new File[0]);
        }

        return new File[0];
    }

    @Override
    public boolean copy(File src, File dest) {
        try {
            FileUtils.copyFile(src, dest);
            return true;
        } catch (Throwable e) {
            // ignore
        }

        DocumentFile srcF = getFile(app, src, false);
        DocumentFile destF = getFile(app, dest, true);

        if (srcF == null) {
            LOG.error("Unable to obtain document for file: " + src);
            return false;
        }

        if (destF == null) {
            LOG.error("Unable to obtain or create document for file: " + dest);
            return false;
        }

        return copy(app, srcF, destF);
    }

    @Override
    public boolean write(File file, byte[] data) {
        try {
            FileUtils.writeByteArrayToFile(file, data);
            return true;
        } catch (IOException e) {
            // ignore
        }

        DocumentFile f = getFile(app, file, true);

        if (f == null) {
            LOG.error("Unable to obtain document for file: " + file);
            return false;
        }

        return write(app, f, data);
    }

    @Override
    public void scan(File file) {
        try {
            final List<String> paths = new LinkedList<>();
            if (isDirectory(file)) {
                walk(file, new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return true;
                    }

                    @Override
                    public void file(File file) {
                        if (!file.isDirectory() && !file.getName().contains(".parts")) {
                            paths.add(file.getPath());
                        }
                    }
                });
            } else {
                paths.add(file.getPath()); // in case we are in a SD and it's an actual file
            }

            if (paths.size() > 0) {
                // We're usually called from SessionManager-alertsLoop, don't hug that loop
                SystemUtils.safePost(
                        Librarian.instance().getHandler(), () -> {
                            MediaScanner.scanFiles(app, paths);
                            UIUtils.broadcastAction(app, Constants.ACTION_FILE_ADDED_OR_REMOVED);
                        });
            }
        } catch (Throwable e) {
            LOG.error("Error scanning file/directory: " + file, e);
        }
    }

    @Override
    public void walk(File file, FileFilter filter) {
        DefaultFileSystem.walkFiles(this, file, filter);
    }

    public String getTreePath(Uri treeUri) {
        return getPath(app, treeUri, true);
    }

    public String getDocumentPath(Uri treeUri) {
        return getPath(app, treeUri, false);
    }

    public DocumentFile getDocument(File file) {
        return getDocument(app, file);
    }

    public String getExtSdCardFolder(File file) {
        return getExtSdCardFolder(app, file);
    }

    public int openFD(File file, String mode) {
        if (!("r".equals(mode) || "w".equals(mode) || "rw".equals(mode))) {
            LOG.error("Only r, w or rw modes supported");
            return -1;
        }

        DocumentFile f = getFile(app, file, true);
        if (f == null) {
            LOG.error("Unable to obtain or create document for file: " + file);
            return -1;
        }

        try {
            ContentResolver cr = app.getContentResolver();
            ParcelFileDescriptor fd = cr.openFileDescriptor(f.getUri(), mode);
            if (fd == null) {
                return -1;
            }
            return fd.detachFd();
        } catch (Throwable e) {
            LOG.error("Unable to get native fd", e);
            return -1;
        }
    }

    private static DocumentFile getDirectory(Context context, File dir, boolean create) {
        try {
            String path = dir.getAbsolutePath();
            DocumentFile cached = CACHE.get(path);
            if (cached != null && cached.isDirectory()) {
                return cached;
            }

            String baseFolder = getExtSdCardFolder(context, dir);
            if (baseFolder == null) {
                if (create) {
                    return dir.mkdirs() ? DocumentFile.fromFile(dir) : null;
                } else {
                    return dir.isDirectory() ? DocumentFile.fromFile(dir) : null;
                }
            }

            baseFolder = combineRoot(baseFolder);

            String fullPath = dir.getAbsolutePath();
            String relativePath = baseFolder.length() < fullPath.length() ? fullPath.substring(baseFolder.length() + 1) : "";

            String[] segments = relativePath.split("/");

            Uri rootUri = getDocumentUri(context, new File(baseFolder));
            DocumentFile f = DocumentFile.fromTreeUri(context, rootUri);

            // special FrostWire case
            if (create) {
                if (baseFolder.endsWith("/FrostWire") && !f.exists()) {
                    baseFolder = baseFolder.substring(0, baseFolder.length() - 10);
                    rootUri = getDocumentUri(context, new File(baseFolder));
                    f = DocumentFile.fromTreeUri(context, rootUri);
                    f = f.findFile("FrostWire");
                    if (f == null) {
                        f = f.createDirectory("FrostWire");
                        if (f == null) {
                            return null;
                        }
                    }
                }
            }
            for (String segment : segments) {
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

            f = f.isDirectory() ? f : null;

            if (f != null) {
                CACHE.put(path, f);
            }

            return f;
        } catch (Throwable e) {
            LOG.error("Error getting directory: " + dir, e);
            return null;
        }
    }

    private static DocumentFile getFile(Context context, File file, boolean create) {
        try {
            String path = file.getAbsolutePath();
            DocumentFile cached = CACHE.get(path);
            if (cached != null && cached.isFile()) {
                return cached;
            }

            File parent = file.getParentFile();
            if (parent == null) {
                return DocumentFile.fromFile(file);
            }

            DocumentFile f = getDirectory(context, parent, false);
            if (f == null && create) {
                f = getDirectory(context, parent, create);
            }

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

            if (f != null) {
                CACHE.put(path, f);
            }

            return f;
        } catch (Throwable e) {
            LOG.error("Error getting file: " + file, e);
            return null;
        }
    }

    private static DocumentFile getDocument(Context context, File file) {
        try {
            String path = file.getAbsolutePath();
            DocumentFile cached = CACHE.get(path);
            if (cached != null) {
                return cached;
            }

            String baseFolder = getExtSdCardFolder(context, file);
            if (baseFolder == null) {
                return file.exists() ? DocumentFile.fromFile(file) : null;
            }

            baseFolder = combineRoot(baseFolder);

            String fullPath = file.getAbsolutePath();
            String relativePath = baseFolder.length() < fullPath.length() ? fullPath.substring(baseFolder.length() + 1) : "";

            String[] segments = relativePath.split("/");

            Uri rootUri = getDocumentUri(context, new File(baseFolder));
            DocumentFile f = DocumentFile.fromTreeUri(context, rootUri);
            for (String segment : segments) {
                DocumentFile child = f.findFile(segment);
                if (child != null) {
                    f = child;
                } else {
                    return null;
                }
            }

            if (f != null) {
                CACHE.put(path, f);
            }

            return f;
        } catch (Throwable e) {
            LOG.error("Error getting document: " + file, e);
            return null;
        }
    }

    private static Uri getDocumentUri(Context context, File file) {
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
        relativePath = relativePath.replace(" ", "%20");
        String uri = "content://com.android.externalstorage.documents/tree/" + volumeId + "%3A" + relativePath;

        return Uri.parse(uri);
    }

    private static String getPath(Context context, Uri treeUri, boolean tree) {
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

        String documentPath = getDocumentPathFromTreeUri(treeUri, tree);
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
        if (file.getAbsolutePath().contains("/Android/data/")) {
            return null;
        }

        String[] extSdPaths = getExtSdCardPaths(context);
        try {
            for (String extSdPath : extSdPaths) {
                if (file.getCanonicalPath().startsWith(extSdPath)) {
                    return extSdPath;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String[] getExtSdCardPaths(Context context) {
        List<String> paths = new ArrayList<>();
        File[] externals = ContextCompat.getExternalFilesDirs(context, "external");
        File external = context.getExternalFilesDir("external");
        for (File file : externals) {
            if (file != null && !file.equals(external)) {
                String absolutePath = file.getAbsolutePath();
                int index = absolutePath.lastIndexOf("/Android/data");
                if (index >= 0) {
                    String path = absolutePath.substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                } else {
                    LOG.warn("ext sd card path wrong: " + absolutePath);
                }
            }
        }
        // special hard coded paths for more security
        for (String path : FIXED_SDCARD_PATHS) {
            if (!paths.contains(path)) {
                paths.add(path);
            }
        }

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

    private static String getDocumentPathFromTreeUri(final Uri treeUri, boolean tree) {
        final String docId = tree ? getTreeDocumentId(treeUri) : getDocumentDocumentId(treeUri);
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

    private static String getDocumentDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 4 && "document".equals(paths.get(2))) {
            return paths.get(3);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    private static String combineRoot(String baseFolder) {
        String root = Platforms.appSettings().string(Constants.PREF_KEY_STORAGE_PATH);

        return root != null && root.startsWith(baseFolder) ? root : baseFolder;
    }

    //------------ more tools methods

    private static boolean copy(Context context, DocumentFile source, DocumentFile target) {
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = context.getContentResolver().openInputStream(source.getUri());
            outStream = openOutputStream(context, target);

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

    private static boolean write(Context context, DocumentFile f, byte[] data) {
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = new ByteArrayInputStream(data);
            outStream = openOutputStream(context, f);

            byte[] buffer = new byte[16384]; // MAGIC_NUMBER
            int bytesRead;
            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }

        } catch (Throwable e) {
            LOG.error("Error when writing bytes to " + f.getUri(), e);
            return false;
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);
        }

        return true;
    }

    private static OutputStream openOutputStream(Context context, DocumentFile f) throws IOException {
        ContentResolver cr = context.getContentResolver();
        ParcelFileDescriptor pfd = cr.openFileDescriptor(f.getUri(), "rw");

        int fd = pfd.detachFd(); // this trick the internal system to trigger the media scanner on nothing
        pfd = ParcelFileDescriptor.adoptFd(fd);

        return new AutoSyncOutputStream(pfd);
    }

    private static List<String> buildFixedSdCardPaths() {
        LinkedList<String> l = new LinkedList<>();

        l.add("/storage/sdcard1"); // Motorola Xoom
        l.add("/storage/extsdcard"); // Samsung SGS3
        l.add("/storage/sdcard0/external_sdcard"); // user request
        l.add("/mnt/extsdcard");
        l.add("/mnt/sdcard/external_sd"); // Samsung galaxy family
        l.add("/mnt/external_sd");
        l.add("/mnt/media_rw/sdcard1"); // 4.4.2 on CyanogenMod S3
        l.add("/removable/microsd"); // Asus transformer prime
        l.add("/mnt/emmc");
        l.add("/storage/external_SD"); // LG
        l.add("/storage/ext_sd"); // HTC One Max
        l.add("/storage/removable/sdcard1"); // Sony Xperia Z1
        l.add("/data/sdext");
        l.add("/data/sdext2");
        l.add("/data/sdext3");
        l.add("/data/sdext4");

        return Collections.unmodifiableList(l);
    }

    private static final class AutoSyncOutputStream extends ParcelFileDescriptor.AutoCloseOutputStream {

        public AutoSyncOutputStream(ParcelFileDescriptor fd) {
            super(fd);
        }

        @Override
        public void close() throws IOException {
            sync();
            super.close();
        }

        private void sync() {
            try {
                getFD().sync();
            } catch (Throwable e) {
                // ignore
            }
        }
    }
}
