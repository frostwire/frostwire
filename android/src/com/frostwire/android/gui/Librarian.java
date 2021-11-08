/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.system.Os;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.AndroidPaths;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.core.player.EphemeralPlaylist;
import com.frostwire.android.core.player.PlaylistItem;
import com.frostwire.android.core.providers.TableFetcher;
import com.frostwire.android.core.providers.TableFetchers;
import com.frostwire.android.gui.transfers.Transfers;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;
import com.frostwire.util.Ref;
import com.frostwire.util.StringUtils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import okio.BufferedSink;
import okio.Okio;

/**
 * The Librarian is in charge of:
 * -> Keeping track of what files we're sharing or not.
 * -> Indexing the files we're sharing.
 * -> Searching for files we're sharing.
 *
 * @author gubatron
 * @author aldenml
 */
public final class Librarian {

    private static final String TAG = "FW.Librarian";
    private static final Logger LOG = Logger.getLogger(Librarian.class);
    private static final Object instanceCreationLock = new Object();
    private static Librarian instance;
    private Handler handler;

    public static Librarian instance() {
        if (instance != null) { // quick check to avoid lock
            return instance;
        }

        synchronized (instanceCreationLock) {
            if (instance == null) {
                instance = new Librarian();
            }
            return instance;
        }
    }

    private Librarian() {
        initHandler();
    }

    public void shutdownHandler() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    // Called by MyFileFragment.loadInBackground() -> createLoaderFiles() -> CreateLoaderFilesAsyncTaskLoader.loadInBackground()
    //
    public List<FWFileDescriptor> getFilesInAndroidMediaStore(final Context context, byte fileType, int offset, int pageSize) {
        return getFilesInAndroidMediaStore(context, offset, pageSize, TableFetchers.getFetcher(fileType));
    }

    public List<FWFileDescriptor> getFilesInAndroidMediaStore(final Context context, byte fileType, String where, String[] whereArgs) {
        return getFilesInAndroidMediaStore(context, 0, Integer.MAX_VALUE, TableFetchers.getFetcher(fileType), where, whereArgs);
    }

    /**
     * @param fileType the file type
     * @return the number of files registered in the providers
     */
    public int getNumFiles(Context context, byte fileType) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);
        if (fetcher == null) {
            return 0;
        }

        Cursor c = null;

        int numFiles = 0;

        try {
            ContentResolver cr = context.getContentResolver();
            Uri externalContentUri = fetcher.getExternalContentUri();
            List<Uri> contentUris = new ArrayList<>();
            if (externalContentUri != null) {
                contentUris.add(externalContentUri);
            }
            for (Uri contentUri : contentUris) {
                c = cr.query(contentUri, new String[]{"count(" + BaseColumns._ID + ")"},
                        fetcher.where(), fetcher.whereArgs(), null);
                numFiles += c != null && c.moveToFirst() ? c.getInt(0) : 0;
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to get num of files", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return numFiles;
    }

    public FWFileDescriptor getFileDescriptor(final Context context, byte fileType, int fileId) {
        List<FWFileDescriptor> fds = getFilesInAndroidMediaStore(context, 0, 1, TableFetchers.getFetcher(fileType), BaseColumns._ID + "=?", new String[]{String.valueOf(fileId)});
        if (fds.size() > 0) {
            return fds.get(0);
        } else {
            return null;
        }
    }

    public String renameFile(final Context context, FWFileDescriptor fd, String newFileName) {
        try {
            String filePath = fd.filePath;
            File oldFile = new File(filePath);
            String ext = FilenameUtils.getExtension(filePath);
            File newFile = new File(oldFile.getParentFile(), newFileName + '.' + ext);
            ContentResolver cr = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaColumns.DATA, newFile.getAbsolutePath());
            values.put(MediaColumns.DISPLAY_NAME, FilenameUtils.getBaseName(newFileName));
            values.put(MediaColumns.TITLE, FilenameUtils.getBaseName(newFileName));
            TableFetcher fetcher = TableFetchers.getFetcher(fd.fileType);

            if (fetcher != null && fetcher.getExternalContentUri() != null) {
                try {
                    cr.update(fetcher.getExternalContentUri(), values, BaseColumns._ID + "=?", new String[]{String.valueOf(fd.id)});
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            }

            //noinspection ResultOfMethodCallIgnored
            oldFile.renameTo(newFile);
            return newFile.getAbsolutePath();
        } catch (Throwable e) {
            Log.e(TAG, "Failed to rename file: " + fd, e);
        }
        return null;
    }

    /**
     * Deletes files.
     * If the fileType is audio it'll use MusicUtils.deleteTracks and
     * tell apollo to clean everything there, playslists, recents, etc.
     */
    public void deleteFiles(final Context context, byte fileType, Collection<FWFileDescriptor> fds) {
        List<Integer> ids = new ArrayList<>(fds.size());
        final int audioMediaType = MediaType.getAudioMediaType().getId();
        if (fileType == audioMediaType) {
            ArrayList<Long> trackIdsToDelete = new ArrayList<>();
            for (FWFileDescriptor fd : fds) {
                // just in case, as we had similar checks in other code
                if (fd.fileType == audioMediaType) {
                    trackIdsToDelete.add((long) fd.id);
                    ids.add(fd.id);
                }
            }
            // wish I could do just trackIdsToDelete.toArray(new long[0]) ...
            long[] songsArray = new long[trackIdsToDelete.size()];
            int i = 0;
            for (Long l : trackIdsToDelete) {
                songsArray[i++] = l;
            }
            try {
                MusicUtils.deleteTracks(context, songsArray, false);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            for (FWFileDescriptor fd : fds) {
                ids.add(fd.id);
            }
        }

        try {
            if (context != null) {
                ContentResolver cr = context.getContentResolver();
                TableFetcher fetcher = TableFetchers.getFetcher(fileType);

                try {
                    if (fetcher != null && fetcher.getExternalContentUri() != null) {
                        cr.delete(fetcher.getExternalContentUri(), MediaColumns._ID + " IN " + buildSet(ids), null);
                    }
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            } else {
                Log.e(TAG, "Failed to delete files from media store, no context available");
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to delete files from media store", e);
        }

        FileSystem fs = Platforms.fileSystem();
        for (FWFileDescriptor fd : fds) {
            try {
                fs.delete(new File(fd.filePath));
            } catch (Throwable ignored) {
            }
        }

        UIUtils.broadcastAction(context,
                Constants.ACTION_FILE_ADDED_OR_REMOVED,
                new UIUtils.IntentByteExtra(Constants.EXTRA_REFRESH_FILE_TYPE, fileType));
    }

    /**
     * @see com.frostwire.android.gui.transfers.UIBTDownloadListener finished() calls this when a torrent download ends
     * on both the torrents folder and the data folder.
     * @see com.frostwire.transfers.BaseHttpDownload moveAndComplete() for http transfers
     * @see FileSystem scan() makes use of this function.
     */
    public void scan(final Context context, File file) {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            SystemUtils.safePost(handler, () -> scan(context, file, Transfers.getIgnorableFiles()));
            return;
        }
        scan(context, file, Transfers.getIgnorableFiles());
        if (context == null) {
            Log.w(TAG, "Librarian has no `context` object to scan() with.");
            return;
        }
        UIUtils.broadcastAction(context, Constants.ACTION_FILE_ADDED_OR_REMOVED);
    }

    public void syncMediaStore(final WeakReference<Context> contextRef) {
        if (!SystemUtils.hasAndroid10OrNewer() && !SystemUtils.isPrimaryExternalStorageMounted()) {
            return;
        }
        SystemUtils.safePost(handler, () -> syncMediaStoreSupport(contextRef));
    }

    public EphemeralPlaylist createEphemeralPlaylist(final Context context, FWFileDescriptor fd) {

        if (!fd.deletable) {
            List<FWFileDescriptor> fds = getFilesInAndroidMediaStore(context, Constants.FILE_TYPE_AUDIO, FilenameUtils.getPath(fd.filePath), false);

            if (fds.size() == 0) { // just in case
                Log.w(TAG, "Logic error creating ephemeral playlist");
                fds.add(fd);
            }

            EphemeralPlaylist playlist = new EphemeralPlaylist(fds);
            playlist.setNextItem(new PlaylistItem(fd));

            return playlist;
        } else {
            List<FWFileDescriptor> fsListOfOne = new ArrayList<>();
            fsListOfOne.add(fd);
            EphemeralPlaylist playlist = new EphemeralPlaylist(fsListOfOne);
            playlist.setNextItem(new PlaylistItem(fd));

            return playlist;
        }
    }

    private void syncMediaStoreSupport(final WeakReference<Context> contextRef) {
        if (!Ref.alive(contextRef)) {
            return;
        }
        Context context = contextRef.get();
        Set<File> ignorableFiles = Transfers.getIgnorableFiles();
        syncMediaStore(context, Constants.FILE_TYPE_AUDIO, ignorableFiles);
        syncMediaStore(context, Constants.FILE_TYPE_PICTURES, ignorableFiles);
        syncMediaStore(context, Constants.FILE_TYPE_VIDEOS, ignorableFiles);
        syncMediaStore(context, Constants.FILE_TYPE_RINGTONES, ignorableFiles);
        syncMediaStore(context, Constants.FILE_TYPE_DOCUMENTS, ignorableFiles);
        Platforms.fileSystem().scan(Platforms.torrents());
        Platforms.fileSystem().scan(BTEngine.ctx.dataDir);
    }

    private void syncMediaStore(final Context context, byte fileType, Set<File> ignorableFiles) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);

        if (fetcher == null) {
            return;
        }

        try {
            ContentResolver cr = context.getContentResolver();
            deleteIgnorableFilesFromVolume(cr, fetcher.getExternalContentUri(), ignorableFiles);
        } catch (Throwable e) {
            Log.e(TAG, "General failure during sync of MediaStore", e);
        }
    }

    private void deleteIgnorableFilesFromVolume(ContentResolver cr, Uri volumeUri, Set<File> ignorableFiles) {
        if (volumeUri == null) {
            return;
        }
        String where = MediaColumns.DATA + " LIKE ?";
        String[] whereArgs = new String[]{Platforms.data() + "%"};

        Cursor c = cr.query(volumeUri, new String[]{MediaColumns._ID, MediaColumns.DATA}, where, whereArgs, null);
        if (c == null) {
            return;
        }

        int idCol = c.getColumnIndex(MediaColumns._ID);
        int pathCol = c.getColumnIndex(MediaColumns.DATA);

        List<Integer> ids = new ArrayList<>(0);

        while (c.moveToNext()) {
            int id = Integer.parseInt(c.getString(idCol));
            String path = c.getString(pathCol);

            if (ignorableFiles.contains(new File(path))) {
                ids.add(id);
            }
        }

        try {
            if (ids.size()  >  0) {
                cr.delete(volumeUri, MediaColumns._ID + " IN " + buildSet(ids), null);
            }
        } catch (Throwable e) {
            Log.e(TAG, "General failure during sync of MediaStore", e);
        } finally {
            c.close();
        }
    }

    private List<FWFileDescriptor> getFilesInAndroidMediaStore(final Context context, int offset, int pageSize, TableFetcher fetcher) {
        return getFilesInAndroidMediaStore(context, offset, pageSize, fetcher, null, null);
    }

    /**
     * Returns a list of FWFileDescriptors.
     *
     * @param offset   - from where (starting at 0)
     * @param pageSize - how many results
     * @param fetcher  - An implementation of TableFetcher
     * @return List<FileDescriptor>
     */
    public List<FWFileDescriptor> getFilesInAndroidMediaStore(
            final Context context,
            final int offset,
            final int pageSize,
            final TableFetcher fetcher,
            String where,
            String[] whereArgs) {
        final List<FWFileDescriptor> result = new ArrayList<>(0);

        if (context == null || fetcher == null) {
            return result;
        }

        try {
            ContentResolver cr = context.getContentResolver();
            String[] columns = fetcher.getColumns();
            String sort = fetcher.getSortByExpression();

            if (where == null) {
                where = fetcher.where();
                whereArgs = fetcher.whereArgs();
            }

            try {
                getFilesInVolume(cr, fetcher.getExternalContentUri(), offset, pageSize, columns, sort,
                        where, whereArgs, fetcher, result);
            } catch (Throwable t) {
                Log.e(TAG, "getFiles::getFilesInVolume failed with fetcher.getExternalContentUri() = " + fetcher.getExternalContentUri(), t);
            }
        } catch (Throwable e) {
            Log.e(TAG, "General failure getting files", e);
        }
        return result;
    }

    public void getFilesInVolume(final ContentResolver cr,
                                 final Uri volumeUri,
                                 final int offset,
                                 final int pageSize,
                                 final String[] columns,
                                 final String sort,
                                 final String where,
                                 final String[] whereArgs,
                                 final TableFetcher fetcher,
                                 final List<FWFileDescriptor> result) {
        if (volumeUri == null) {
            return;
        }
        try {
            Cursor c = cr.query(volumeUri, columns, where, whereArgs, sort);
            if (c == null || !c.moveToPosition(offset)) {
                return;
            }
            fetcher.prepareColumnIds(c);
            int count = 1;
            do {
                FWFileDescriptor fd = fetcher.fetchFWFileDescriptor(c);
                result.add(fd);
            } while (c.moveToNext() && count++ < pageSize);
            IOUtils.closeQuietly(c);
        } catch (Throwable e) {
            LOG.error(e.getMessage() + " volumeUri=" + volumeUri, e);
        }
    }

    public List<FWFileDescriptor> getFilesInAndroidMediaStore(final Context context, String filepath, boolean exactPathMatch) {
        return getFilesInAndroidMediaStore(context, AndroidPaths.getFileType(filepath, true), filepath, exactPathMatch);
    }

    /**
     * @param exactPathMatch - set it to false and pass an incomplete filepath prefix to get files in a folder for example.
     */
    public List<FWFileDescriptor> getFilesInAndroidMediaStore(final Context context, byte fileType, String filepath, boolean exactPathMatch) {
        String where = MediaColumns.DATA + " LIKE ?";
        String[] whereArgs = new String[]{(exactPathMatch) ? filepath : "%" + filepath + "%"};
        return getFilesInAndroidMediaStore(context, fileType, where, whereArgs);
    }

    public Thread getHandlerThread() {
        return handler.getLooper().getThread();
    }

    public static boolean createSymLink(String originalFilePath, String symLinkFilePath) {
        try {
            Os.symlink(originalFilePath, symLinkFilePath);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void scan(final Context context, File file, Set<File> ignorableFiles) {
        //if we just have a single file, do it the old way
        if (file.isFile()) {
            if (ignorableFiles.contains(file)) {
                return;
            }
            if (SystemUtils.hasAndroid10OrNewer()) {
                // Can't use Media Scanner after Android 10 Scoped storage changes.
                // MediaScanner is supposedly invoked internally when we perform MediaStore inserts/updates
                // it will set the DATA field for us, so don't try to write it manually, doesn't keep
                // whatever path you put in there
                mediaStoreInsert(context, file);
            } else {
                new UniversalScanner(context).scan(file.getAbsolutePath());
            }
        } else if (file.isDirectory() && file.canRead()) {
            Collection<File> flattenedFiles = getAllFolderFiles(file, null);

            if (ignorableFiles != null && !ignorableFiles.isEmpty()) {
                flattenedFiles.removeAll(ignorableFiles);
            }

            if (!flattenedFiles.isEmpty()) {
                if (SystemUtils.hasAndroid10OrNewer()) {
                    flattenedFiles.forEach(f -> mediaStoreInsert(context, f));
                } else {
                    new UniversalScanner(context).scan(flattenedFiles);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void copyFileBytesToMediaStore(ContentResolver contentResolver,
                                           File srcFile,
                                           ContentValues values,
                                           Uri insertedUri) {
        try {
            OutputStream outputStream = contentResolver.openOutputStream(insertedUri);
            if (outputStream == null) {
                LOG.error("copyFileBytesToMediaStore failed, could not get an output stream from insertedUri=" + insertedUri);
                return;
            }
            BufferedSink sink = Okio.buffer(Okio.sink(outputStream));
            sink.writeAll(Okio.source(srcFile));
            sink.flush();
            sink.close();
        } catch (Throwable t) {
            LOG.error("copyFileBytesToMediaStore error: " + t.getMessage(), t);
            return;
        }
        values.clear();
        values.put(MediaColumns.IS_PENDING, 0);
        contentResolver.update(insertedUri, values, null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void mediaStoreInsert(Context context, File srcFile) {
        if (srcFile.isDirectory()) {
            return;
        }
        Uri audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        // Add to MediaStore
        ContentResolver resolver = context.getContentResolver();
        byte fileType = AndroidPaths.getFileType(srcFile.getAbsolutePath(), true);
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);
        Uri mediaStoreCollectionUri = Objects.requireNonNull(fetcher).getExternalContentUri();
        String relativeFolderPath = AndroidPaths.getRelativeFolderPath(srcFile);

        if (alreadyInMediaStore(context, fetcher, srcFile.getName(), relativeFolderPath)) {
            LOG.info("mediaStoreInsert: alreadyInMediaStore skipping " + srcFile.getAbsolutePath());
            return;
        }

        LOG.info("mediaStoreInsert -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI = " + audioUri);
        LOG.info("mediaStoreInsert -> mediaStoreCollectionUri = " + mediaStoreCollectionUri);
        LOG.info("mediaStoreInsert -> relativeFolderPath: " + relativeFolderPath);

        ContentValues values = new ContentValues();
        values.put(MediaColumns.IS_PENDING, 1);

        if (!StringUtils.isNullOrEmpty(relativeFolderPath)) {
            values.put(MediaColumns.RELATIVE_PATH, relativeFolderPath);
        } else {
            LOG.info("WARNING, relative relativeFolderPath is null for " + srcFile.getAbsolutePath());
        }

        values.put(MediaColumns.DISPLAY_NAME, srcFile.getName());
        values.put(MediaColumns.MIME_TYPE, MimeDetector.getMimeType(FilenameUtils.getExtension(srcFile.getName())));
        values.put(MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000);
        values.put(MediaColumns.SIZE, srcFile.length());

        if (fileType == Constants.FILE_TYPE_AUDIO || fileType == Constants.FILE_TYPE_VIDEOS) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(srcFile.getAbsolutePath());
            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            LOG.info("mediaStoreInsert title (MediaDataRetriever): " + title);
            if (title != null) {
                values.put(MediaColumns.TITLE, title);
                values.put(MediaColumns.DISPLAY_NAME, srcFile.getName());
            }
        } else {
            values.put(MediaColumns.TITLE, srcFile.getName());
        }
        Uri insertedUri = resolver.insert(mediaStoreCollectionUri, values);
        if (insertedUri == null) {
            LOG.error("mediaStoreInsert -> could not perform media store insertion");
            return;
        }
        LOG.info("mediaStoreInsert -> insertedUri = " + insertedUri);
        copyFileBytesToMediaStore(resolver, srcFile, values, insertedUri);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private boolean alreadyInMediaStore(Context context,
                                        TableFetcher fetcher,
                                        String displayName,
                                        String relativeFolderPath) {
        String where = MediaColumns.DISPLAY_NAME + " = ? AND " + MediaColumns.RELATIVE_PATH + " = ?";
        String[] whereArgs = new String[]{displayName, relativeFolderPath};
        List<FWFileDescriptor> filesInAndroidMediaStore =
                getFilesInAndroidMediaStore(context,
                        0, 1, fetcher, where, whereArgs);
        return filesInAndroidMediaStore.size() > 0;
    }

    private void initHandler() {
        final HandlerThread handlerThread = new HandlerThread("Librarian::handler",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    /**
     * Given a folder path it'll return all the files contained within it and it's subfolders
     * as a flat set of Files.
     * <p>
     * Non-recursive implementation, up to 20% faster in tests than recursive implementation. :)
     *
     * @param extensions If you only need certain files filtered by their extensions, use this string array (without the "."). or set to null if you want all files. e.g. ["txt","jpg"] if you only want text files and jpegs.
     * @return The set of files.
     * @author gubatron
     */
    private static Collection<File> getAllFolderFiles(File folder, String[] extensions) {
        Set<File> results = new HashSet<>();
        Stack<File> subFolders = new Stack<>();
        File currentFolder = folder;
        while (currentFolder != null && currentFolder.isDirectory() && currentFolder.canRead()) {
            File[] fs = null;
            try {
                fs = currentFolder.listFiles();
            } catch (SecurityException e) {
                LOG.error(e.getMessage(), e);
            }

            if (fs != null && fs.length > 0) {
                for (File f : fs) {
                    if (!f.isDirectory()) {
                        if (extensions == null || FilenameUtils.isExtension(f.getName(), extensions)) {
                            results.add(f);
                        }
                    } else {
                        subFolders.push(f);
                    }
                }
            }

            if (!subFolders.isEmpty()) {
                currentFolder = subFolders.pop();
            } else {
                currentFolder = null;
            }
        }
        return results;
    }

    private static String buildSet(List<?> list) {
        StringBuilder sb = new StringBuilder("(");
        int i = 0;
        for (Object id : list) {
            sb.append(id);
            if (i++ < (list.size() - 1)) {
                sb.append(",");
            }
        }
        sb.append(")");

        return sb.toString();
    }

    public Handler getHandler() {
        return handler;
    }
}
