/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import androidx.annotation.NonNull;
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
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Logger;
import com.frostwire.util.MimeDetector;
import com.frostwire.util.StringUtils;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    public List<FWFileDescriptor> getFilesInAndroidMediaStore(final Context context, byte fileType, String where, String[] whereArgs) {
        return getFilesInAndroidMediaStore(context, 0, Integer.MAX_VALUE, TableFetchers.getFetcher(fileType), where, whereArgs);
    }

    /**
     * @param fileType the file type
     * @return the number of files registered in the providers
     */
    public int getNumFiles(Context context, byte fileType) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);
        if (fetcher == TableFetchers.UNKNOWN_TABLE_FETCHER) {
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
            LOG.error("Failed to get num of files", e);
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

            if (fetcher != TableFetchers.UNKNOWN_TABLE_FETCHER &&
                    fetcher.getExternalContentUri() != null) {
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
            LOG.error("Failed to rename file: " + fd, e);
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
                    if (fetcher != TableFetchers.UNKNOWN_TABLE_FETCHER && fetcher.getExternalContentUri() != null) {
                        cr.delete(fetcher.getExternalContentUri(), MediaColumns._ID + " IN " + buildSet(ids), null);
                    }
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            } else {
                LOG.error("Failed to delete files from media store, no context available");
            }
        } catch (Throwable e) {
            LOG.error("Failed to delete files from media store", e);
        }

        FileSystem fs = Platforms.fileSystem();
        for (FWFileDescriptor fd : fds) {
            try {
                fs.delete(new File(fd.filePath));
            } catch (Throwable ignored) {
            }
        }
    }

    public EphemeralPlaylist createEphemeralPlaylist(final Context context, FWFileDescriptor fd) {
        if (!fd.deletable) {
            List<FWFileDescriptor> fds = getFilesInAndroidMediaStore(context, Constants.FILE_TYPE_AUDIO, FilenameUtils.getPath(fd.filePath), false);

            if (fds.size() == 0) { // just in case
                LOG.error("Logic error creating ephemeral playlist");
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

        if (context == null || fetcher == null || fetcher == TableFetchers.UNKNOWN_TABLE_FETCHER) {
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
                LOG.error("getFiles::getFilesInVolume failed with fetcher.getExternalContentUri() = " + fetcher.getExternalContentUri(), t);
            }
        } catch (Throwable e) {
            LOG.error("General failure getting files", e);
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
        filepath = filepath.replace(Platforms.get().systemPaths().data().getAbsolutePath(), "");
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

    private void scan(final Context context, @NonNull File file, Set<File> ignorableFiles) {
        //if we just have a single file, do it the old way
        if (file.isFile()) {
            if (ignorableFiles.contains(file)) {
                return;
            }
            if (!SystemUtils.hasAndroid10OrNewer()) {
                new UniversalScanner(context).scan(file.getAbsolutePath());
            }
        } else if (file.isDirectory() && file.canRead()) {
            Collection<File> flattenedFiles = getAllFolderFiles(file, null);

            if (ignorableFiles != null && !ignorableFiles.isEmpty()) {
                flattenedFiles.removeAll(ignorableFiles);
            }

            if (!flattenedFiles.isEmpty()) {
                if (!SystemUtils.hasAndroid10OrNewer()) {
                    new UniversalScanner(context).scan(flattenedFiles);
                }
            }
        }
    }

    /**
     * This method assumes you did the logic to determine the target location in Downloads.
     * Meaning, "destInDownloads" doesn't exist yet, but this is where you'd like it to be saved at.
     *
     * @param src             The actual file wherever else it exists, usually in an internal/external app folder
     * @param destInDownloads The final desired location of the file in the public Downloads folder
     * @return
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean mediaStoreSaveToDownloads(File src, File destInDownloads, boolean copyBytesToMediaStore) {
        LOG.info("Librarian::mediaStoreSaveToDownloads trying to save " + src.getAbsolutePath() + " into " + destInDownloads.getAbsolutePath());

        Context context = SystemUtils.getApplicationContext();
        if (context == null) {
            LOG.info("Librarian::mediaStoreSaveToDownloads aborting. ApplicationContext reference is null, not ready yet.");
            return false;
        }

        String relativePath = AndroidPaths.getRelativeFolderPathFromFileInDownloads(destInDownloads);

        if (Librarian.mediaStoreFileExists(destInDownloads)) {
            LOG.info("Librarian::mediaStoreSaveToDownloads aborting. " + relativePath + "/" + destInDownloads.getName() + " already exists on the media store db");
            return false;
        }

        return mediaStoreInsert(context, src, relativePath, copyBytesToMediaStore);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static boolean mediaStoreFileExists(File destInDownloads) {
        String relativePath = AndroidPaths.getRelativeFolderPathFromFileInDownloads(destInDownloads);
        String displayName = destInDownloads.getName();
        Uri downloadsExternalUri = MediaStore.Downloads.getContentUri("external");
        ContentResolver contentResolver = SystemUtils.getApplicationContext().getContentResolver();
        String selection = MediaColumns.DISPLAY_NAME + " = ? AND " +
                MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[]{displayName, '%' + relativePath + '%'};
        Cursor query = contentResolver.query(downloadsExternalUri, null, selection, selectionArgs, null);
        if (query == null) {
            LOG.info("Librarian::mediaStoreFileExists -> null query for " + displayName);
            return false;
        }
        boolean fileFound = query.getCount() > 0;
        query.close();
        LOG.info("Librarian::mediaStoreFileExists() -> " + fileFound);
        return fileFound;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean mediaStoreInsert(Context context, File srcFile, String relativeFolderPath, boolean copyBytesToMediaStore) {
        if (srcFile.isDirectory()) {
            return false;
        }
        // Add to MediaStore
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaColumns.IS_PENDING, copyBytesToMediaStore ? 1 : 0);

        if (!StringUtils.isNullOrEmpty(relativeFolderPath)) {
            LOG.info("Librarian::mediaStoreInsert using relative path " + relativeFolderPath);
            values.put(MediaColumns.RELATIVE_PATH, relativeFolderPath);
        } else {
            LOG.info("WARNING, relative relativeFolderPath is null for " + srcFile.getAbsolutePath());
        }

        values.put(MediaColumns.DISPLAY_NAME, srcFile.getName());
        values.put(MediaColumns.MIME_TYPE, MimeDetector.getMimeType(FilenameUtils.getExtension(srcFile.getName())));
        values.put(MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000);
        values.put(MediaColumns.SIZE, srcFile.length());

        byte fileType = AndroidPaths.getFileType(srcFile.getAbsolutePath(), true);
        if (fileType == Constants.FILE_TYPE_AUDIO || fileType == Constants.FILE_TYPE_VIDEOS) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            boolean illegalArgumentCaught = false;
            try {
                mmr.setDataSource(srcFile.getAbsolutePath());
            } catch (Throwable ignored) {
                // at first we tried catching illegal argument exception
                // then we started seeing Runtime Exception errors being thrown here.
                illegalArgumentCaught = true;
            }
            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artistName = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String albumArtistName = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            String albumName = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            String durationString = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (title != null) {
                LOG.info("mediaStoreInsert title (MediaDataRetriever): " + title);
                values.put(MediaColumns.TITLE, title);
                values.put(MediaColumns.DISPLAY_NAME, srcFile.getName());

                if (SystemUtils.hasAndroid11OrNewer() && fileType == Constants.FILE_TYPE_AUDIO) {
                    values.put(MediaColumns.ARTIST, artistName);
                    values.put(MediaColumns.ALBUM_ARTIST, albumArtistName);
                    values.put(MediaColumns.ALBUM, albumName);
                    if (!StringUtils.isNullOrEmpty(durationString)) {
                        values.put(MediaColumns.DURATION, Long.parseLong(durationString));
                    }
                }
            } else if (illegalArgumentCaught && title == null) {
                // Something went wrong with mmr.setDataSource()
                // Happens in Android 10
                String fileNameWithoutExtension = srcFile.getName().replace(
                        FilenameUtils.getExtension(srcFile.getName()), "");
                values.put(MediaColumns.TITLE, fileNameWithoutExtension);
                values.put(MediaColumns.DISPLAY_NAME, fileNameWithoutExtension);
            }
        } else {
            values.put(MediaColumns.TITLE, srcFile.getName());
        }
        Uri downloadsExternalUri = MediaStore.Downloads.getContentUri("external");
        try {
            Uri insertedUri = resolver.insert(downloadsExternalUri, values);
            if (insertedUri == null) {
                LOG.error("mediaStoreInsert -> could not perform media store insertion");
                return false;
            }
            LOG.info("mediaStoreInsert -> insertedUri = " + insertedUri);
            if (copyBytesToMediaStore) {
                return copyFileBytesToMediaStore(resolver, srcFile, values, insertedUri);
            }
        } catch (Throwable t) {
            return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean copyFileBytesToMediaStore(ContentResolver contentResolver,
                                                     File srcFile,
                                                     ContentValues values,
                                                     Uri insertedUri) {
        try {
            OutputStream outputStream = contentResolver.openOutputStream(insertedUri);
            if (outputStream == null) {
                LOG.error("Librarian::copyFileBytesToMediaStore failed, could not get an output stream from insertedUri=" + insertedUri);
                return false;
            }
            BufferedSink sink = Okio.buffer(Okio.sink(outputStream));
            sink.writeAll(Okio.source(srcFile));
            sink.flush();
            sink.close();
        } catch (Throwable t) {
            LOG.error("Librarian::copyFileBytesToMediaStore error: " + t.getMessage(), t);
            return false;
        }
        values.clear();
        values.put(MediaColumns.IS_PENDING, 0);
        contentResolver.update(insertedUri, values, null, null);
        return true;
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.Q)
    public static void mediaStoreDeleteFromDownloads(File fileBittorrentTransferItem) {
        File destinationFileInDownloads = AndroidPaths.getDestinationFileFromInternalFileInAndroid10(fileBittorrentTransferItem);
        String relativeFolderPath = AndroidPaths.getRelativeFolderPathFromFileInDownloads(destinationFileInDownloads);
        String displayName = fileBittorrentTransferItem.getName();
        ContentResolver contentResolver = SystemUtils.getApplicationContext().getContentResolver();
        Uri externalUri = MediaStore.Downloads.getContentUri("external");
        String[] projection = {MediaStore.Downloads._ID};
        String selection = MediaStore.Downloads.DISPLAY_NAME + " = ? and " + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = {displayName, "%" + relativeFolderPath + "%"};
        // first we need to get the file URL in the media store, we'll do so with the relative path and display name
        Cursor query = contentResolver.query(externalUri, projection, selection, selectionArgs, null);
        if (query != null && query.getCount() > 0) {
            query.moveToFirst();
            int columnIndex = query.getColumnIndex(projection[0]);
            long fileId = query.getLong(columnIndex);
            query.close();

            Uri fileUri = Uri.parse(externalUri.toString() + "/" + fileId);

            int deleted = contentResolver.delete(fileUri, selection, selectionArgs);
            LOG.info("Librarian::mediaStoreDeleteFromDownloads deleted " + deleted + " files -> " + fileUri + " : " + displayName);
        } else {
            LOG.info("Librarian::mediaStoreDeleteFromDownloads could not delete " + displayName);
        }
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
