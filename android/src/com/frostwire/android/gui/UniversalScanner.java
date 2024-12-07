/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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


package com.frostwire.android.gui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.SystemClock;

import com.frostwire.android.core.Constants;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.FileFilter;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
final class UniversalScanner {

    private static final Logger LOG = Logger.getLogger(UniversalScanner.class);

    private final Context context;

    public UniversalScanner(Context context) {
        this.context = context;
        if (context == null) {
            LOG.warn("UniversalScanner has no `context` object to scan() with.");
        }
    }

    public void scan(final String filePath) {
        scan(Collections.singletonList(new File(filePath)));
    }

    public void scan(final Collection<File> filesToScan) {
        new MultiFileAndroidScanner(filesToScan).scan();
    }

    private final class MultiFileAndroidScanner implements MediaScannerConnectionClient {

        private MediaScannerConnection connection;
        private final Collection<File> files;
        private int numCompletedScans;

        public MultiFileAndroidScanner(Collection<File> filesToScan) {
            this.files = filesToScan;
            numCompletedScans = 0;
        }

        public void scan() {
            try {
                connection = new MediaScannerConnection(context, this);
                connection.connect();
            } catch (Throwable e) {
                LOG.warn("Error scanning file with android internal scanner, one retry", e);
                SystemClock.sleep(1000);
                connection = new MediaScannerConnection(context, this);
                connection.connect();
            }
        }

        public void onMediaScannerConnected() {
            // do not do this on main thread, causing ANRs
            if (SystemUtils.isUIThread()) {
                SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> UniversalScanner.onMediaScannerConnected(connection, files));
            } else {
                UniversalScanner.onMediaScannerConnected(connection, files);
            }
        }

        public void onScanCompleted(String path, Uri uri) {
            /* This will work if onScanCompleted is invoked after scanFile finishes. */
            numCompletedScans++;
            if (numCompletedScans == files.size()) {
                connection.disconnect();
            }

            if (path == null || path.contains("/Android/data/" + context.getPackageName() + "/files/temp")) {
                return;
            }

            MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(path));

            if (uri != null && !path.contains("/Android/data/" + context.getPackageName())) {
                if (mt != null && mt.getId() == Constants.FILE_TYPE_DOCUMENTS) {
                    //scanDocument(path);
                }
            } else {
                if (path.endsWith(".apk")) {
                    //LOG.debug("Can't scan apk for security concerns: " + path);
                } else if (mt != null && mt != MediaType.TYPE_UNKNOWN) {
                    if (mt.getId() == Constants.FILE_TYPE_AUDIO ||
                            mt.getId() == Constants.FILE_TYPE_VIDEOS ||
                            mt.getId() == Constants.FILE_TYPE_PICTURES) {
                        scanPrivateFile(uri, path, mt);
                    }
                } else {
                    //scanDocument(path);
                    //LOG.debug("Scanned new file as document: " + path);
                }
            }
        }
    }

    private static void onMediaScannerConnected(MediaScannerConnection connection, Collection<File> files) {
        if (files == null || connection == null) {
            return;
        }
        try {
            /* should only arrive here on connected state, but let's double check since it's possible */
            if (connection.isConnected() && !files.isEmpty()) {
                for (File f : files) {
                    String path = f.getAbsolutePath();
                    MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(path));
                    if (mt != null && mt != MediaType.TYPE_UNKNOWN) {
                        connection.scanFile(path, mt.getMimeType());
                    } else {
                        connection.scanFile(path, null);
                    }
                }
            }
        } catch (IllegalStateException e) {
            LOG.warn("Scanner service wasn't really connected or service was null", e);
            //should we try to connect again? don't want to end up in endless loop
            //maybe destroy connection?
        }
    }

    /**
     * Android geniuses put a .nomedia file on the .../Android/data/ folder
     * inside the secondary external storage path, therefore, all attempts
     * to use MediaScannerConnection to scan a media file fail. Therefore we
     * have this method to insert the file's metadata manually on the content provider.
     */
    private void scanPrivateFile(Uri oldUri, String filePath, MediaType mt) {
        try {
            if (oldUri == null) {
                oldUri = Uri.fromFile(new File(filePath));
                LOG.debug("oldUri is null, what comes out of Uri.fromFile? " + oldUri);
            }
            if (oldUri != null) {
                int n = context.getContentResolver().delete(oldUri, null, null);
                if (n > 0) {
                    LOG.debug("Deleted from Files provider: " + oldUri + ", path: " + filePath);
                }
            }
        } catch (Throwable e) {
            LOG.error("Unable to scan file: " + filePath, e);
        }
        try {
            nativeScanFile(context, filePath);
        } catch (Throwable e2) {
            LOG.error("Unable to scan file: " + filePath, e2);
        }
    }

    private static Uri nativeScanFile(Context context, String path) {
        try {
            File f = new File(path);

            Class<?> clazz = Class.forName("android.media.MediaScanner");

            Constructor<?> mediaScannerC = clazz.getDeclaredConstructor(Context.class);
            Object scanner = mediaScannerC.newInstance(context);

            try {
                @SuppressLint("SoonBlockedPrivateApi") Method setLocaleM = clazz.getDeclaredMethod("setLocale", String.class);
                setLocaleM.invoke(scanner, Locale.US.toString());
            } catch (Throwable e) {
                e.printStackTrace();
            }

            @SuppressLint("SoonBlockedPrivateApi") Field mClientF = clazz.getDeclaredField("mClient");
            mClientF.setAccessible(true);
            Object mClient = mClientF.get(scanner);

            Method scanSingleFileM = clazz.getDeclaredMethod("scanSingleFile", String.class, String.class, String.class);
            Uri fileUri = (Uri) scanSingleFileM.invoke(scanner, f.getAbsolutePath(), "external", "data/raw");
            int n = context.getContentResolver().delete(fileUri, null, null);
            if (n > 0) {
                LOG.debug("Deleted from Files provider: " + fileUri);
            }

            Field mNoMediaF = mClient.getClass().getDeclaredField("mNoMedia");
            mNoMediaF.setAccessible(true);
            mNoMediaF.setBoolean(mClient, false);

            // This is only for HTC (tested only on HTC One M8)
            try {
                Field mFileCacheF = clazz.getDeclaredField("mFileCache");
                mFileCacheF.setAccessible(true);
                mFileCacheF.set(scanner, new HashMap<String, Object>());
            } catch (Throwable e) {
                // no an HTC, I need some time to refactor this hack
            }

            try {
                Field mFileCacheF = clazz.getDeclaredField("mNoMediaPaths");
                mFileCacheF.setAccessible(true);
                mFileCacheF.set(scanner, new HashMap<String, String>());
            } catch (Throwable e) {
                e.printStackTrace();
            }

            Method doScanFileM = mClient.getClass().getDeclaredMethod("doScanFile", String.class, String.class, long.class, long.class, boolean.class, boolean.class, boolean.class);
            Uri mediaUri = (Uri) doScanFileM.invoke(mClient, f.getAbsolutePath(), null, f.lastModified(), f.length(), false, true, false);

            Method releaseM = clazz.getDeclaredMethod("release");
            releaseM.invoke(scanner);

            return mediaUri;

        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public void scanDir(File privateDir) {
        final List<File> files = new LinkedList<>();
        Platforms.fileSystem().walk(privateDir, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public void file(File file) {
                if (!file.isDirectory()) {
                    files.add(file);
                }
            }
        });
        scan(files);
    }
}
