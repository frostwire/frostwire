/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import android.provider.BaseColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.core.player.EphemeralPlaylist;
import com.frostwire.android.core.player.PlaylistItem;
import com.frostwire.android.core.providers.TableFetcher;
import com.frostwire.android.core.providers.TableFetchers;
import com.frostwire.android.gui.transfers.Transfers;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.util.Ref;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

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

    private static final Object lock = new Object();
    private static Librarian instance;

    public static Librarian instance() {
        if (instance != null) { // quick check to avoid lock
            return instance;
        }

        synchronized (lock) {
            if (instance == null) {
                instance = new Librarian();
            }
            return instance;
        }
    }

    private Librarian() {
    }

    public List<FileDescriptor> getFiles(final Context context, byte fileType, int offset, int pageSize) {
        return getFiles(context, offset, pageSize, TableFetchers.getFetcher(fileType));
    }

    public List<FileDescriptor> getFiles(final Context context, byte fileType, String where, String[] whereArgs) {
        return getFiles(context, 0, Integer.MAX_VALUE, TableFetchers.getFetcher(fileType), where, whereArgs);
    }

    /**
     * @param fileType the file type
     * @return the number of files registered in the providers
     */
    public int getNumFiles(Context context, byte fileType) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);
        Cursor c = null;

        int numFiles = 0;

        try {
            ContentResolver cr = context.getContentResolver();
            c = cr.query(fetcher.getContentUri(), new String[]{"count(" + BaseColumns._ID + ")"},
                    fetcher.where(), fetcher.whereArgs(), null);
            numFiles = c != null && c.moveToFirst() ? c.getInt(0) : 0;
        } catch (Throwable e) {
            Log.e(TAG, "Failed to get num of files", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return numFiles;
    }

    public FileDescriptor getFileDescriptor(final Context context, byte fileType, int fileId) {
        List<FileDescriptor> fds = getFiles(context, 0, 1, TableFetchers.getFetcher(fileType), BaseColumns._ID + "=?", new String[]{String.valueOf(fileId)});
        if (fds.size() > 0) {
            return fds.get(0);
        } else {
            return null;
        }
    }

    public String renameFile(final Context context, FileDescriptor fd, String newFileName) {
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
            cr.update(fetcher.getContentUri(), values, BaseColumns._ID + "=?", new String[]{String.valueOf(fd.id)});
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
     *
     * @param context
     * @param fileType
     * @param fds
     */
    public void deleteFiles(final Context context, byte fileType, Collection<FileDescriptor> fds) {
        List<Integer> ids = new ArrayList<>(fds.size());
        final int audioMediaType = MediaType.getAudioMediaType().getId();
        if (fileType == audioMediaType) {
            ArrayList<Long> trackIdsToDelete = new ArrayList<>();
            for (FileDescriptor fd : fds) {
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
            for (FileDescriptor fd : fds) {
                ids.add(fd.id);
            }
        }

        try {
            if (context != null) {
                ContentResolver cr = context.getContentResolver();
                TableFetcher fetcher = TableFetchers.getFetcher(fileType);
                cr.delete(fetcher.getContentUri(), MediaColumns._ID + " IN " + buildSet(ids), null);
            } else {
                Log.e(TAG, "Failed to delete files from media store, no context available");
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to delete files from media store", e);
        }

        FileSystem fs = Platforms.fileSystem();
        for (FileDescriptor fd : fds) {
            try {
                fs.delete(new File(fd.filePath));
            } catch (Throwable ignored) {
            }
        }

        UIUtils.broadcastAction(context,
                Constants.ACTION_FILE_ADDED_OR_REMOVED,
                new UIUtils.IntentByteExtra(Constants.EXTRA_REFRESH_FILE_TYPE, fileType));
    }

    public void scan(final Context context, File file) {
        scan(context, file, Transfers.getIgnorableFiles());
        if (context == null) {
            Log.w(TAG, "Librarian has no `context` object to scan() with.");
            return;
        }
        UIUtils.broadcastAction(context, Constants.ACTION_FILE_ADDED_OR_REMOVED);
    }

    public void syncMediaStore(final WeakReference<Context> contextRef) {
        if (!SystemUtils.isPrimaryExternalStorageMounted()) {
            return;
        }
        Thread t = new Thread(() -> syncMediaStoreSupport(contextRef));
        t.setName("syncMediaStore");
        t.setDaemon(true);
        t.start();
    }

    public EphemeralPlaylist createEphemeralPlaylist(final Context context, FileDescriptor fd) {
        List<FileDescriptor> fds = getFiles(context, Constants.FILE_TYPE_AUDIO, FilenameUtils.getPath(fd.filePath), false);

        if (fds.size() == 0) { // just in case
            Log.w(TAG, "Logic error creating ephemeral playlist");
            fds.add(fd);
        }

        EphemeralPlaylist playlist = new EphemeralPlaylist(fds);
        playlist.setNextItem(new PlaylistItem(fd));

        return playlist;
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
    }

    private void syncMediaStore(final Context context, byte fileType, Set<File> ignorableFiles) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);

        if (fetcher == null) {
            return;
        }

        Cursor c = null;
        try {

            ContentResolver cr = context.getContentResolver();

            String where = MediaColumns.DATA + " LIKE ?";
            String[] whereArgs = new String[]{Platforms.data() + "%"};

            c = cr.query(fetcher.getContentUri(), new String[]{MediaColumns._ID, MediaColumns.DATA}, where, whereArgs, null);
            if (c == null) {
                return;
            }

            int idCol = c.getColumnIndex(MediaColumns._ID);
            int pathCol = c.getColumnIndex(MediaColumns.DATA);

            List<Integer> ids = new ArrayList<>(0);

            while (c.moveToNext()) {
                int id = Integer.valueOf(c.getString(idCol));
                String path = c.getString(pathCol);

                if (ignorableFiles.contains(new File(path))) {
                    ids.add(id);
                }
            }

            cr.delete(fetcher.getContentUri(), MediaColumns._ID + " IN " + buildSet(ids), null);

        } catch (Throwable e) {
            Log.e(TAG, "General failure during sync of MediaStore", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private List<FileDescriptor> getFiles(final Context context, int offset, int pageSize, TableFetcher fetcher) {
        return getFiles(context, offset, pageSize, fetcher, null, null);
    }

    /**
     * Returns a list of Files.
     *
     * @param offset   - from where (starting at 0)
     * @param pageSize - how many results
     * @param fetcher  - An implementation of TableFetcher
     * @return List<FileDescriptor>
     */
    private List<FileDescriptor> getFiles(final Context context, int offset, int pageSize, TableFetcher fetcher, String where, String[] whereArgs) {
        List<FileDescriptor> result = new ArrayList<>(0);

        if (context == null || fetcher == null) {
            return result;
        }

        Cursor c = null;
        try {
            ContentResolver cr = context.getContentResolver();
            String[] columns = fetcher.getColumns();
            String sort = fetcher.getSortByExpression();

            if (where == null) {
                where = fetcher.where();
                whereArgs = fetcher.whereArgs();
            }

            c = cr.query(fetcher.getContentUri(), columns, where, whereArgs, sort);
            if (c == null || !c.moveToPosition(offset)) {
                return result;
            }

            fetcher.prepare(c);
            int count = 1;
            do {
                FileDescriptor fd = fetcher.fetch(c);
                result.add(fd);
            } while (c.moveToNext() && count++ < pageSize);
        } catch (Throwable e) {
            Log.e(TAG, "General failure getting files", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return result;
    }

    public List<FileDescriptor> getFiles(final Context context, String filepath, boolean exactPathMatch) {
        return getFiles(context, getFileType(filepath, true), filepath, exactPathMatch);
    }

    /**
     * @param fileType
     * @param filepath
     * @param exactPathMatch - set it to false and pass an incomplete filepath prefix to get files in a folder for example.
     * @return
     */
    public List<FileDescriptor> getFiles(final Context context, byte fileType, String filepath, boolean exactPathMatch) {
        String where = MediaColumns.DATA + " LIKE ?";
        String[] whereArgs = new String[]{(exactPathMatch) ? filepath : "%" + filepath + "%"};
        return getFiles(context, fileType, where, whereArgs);
    }

    private void scan(final Context context, File file, Set<File> ignorableFiles) {
        //if we just have a single file, do it the old way
        if (file.isFile()) {
            if (ignorableFiles.contains(file)) {
                return;
            }
            new UniversalScanner(context).scan(file.getAbsolutePath());
        } else if (file.isDirectory() && file.canRead()) {
            Collection<File> flattenedFiles = getAllFolderFiles(file, null);

            if (ignorableFiles != null && !ignorableFiles.isEmpty()) {
                flattenedFiles.removeAll(ignorableFiles);
            }

            if (flattenedFiles != null && !flattenedFiles.isEmpty()) {
                new UniversalScanner(context).scan(flattenedFiles);
            }
        }
    }

    /**
     * Given a folder path it'll return all the files contained within it and it's subfolders
     * as a flat set of Files.
     * <p>
     * Non-recursive implementation, up to 20% faster in tests than recursive implementation. :)
     *
     * @param folder
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

    private byte getFileType(String filename, boolean returnTorrentsAsDocument) {
        byte result = Constants.FILE_TYPE_DOCUMENTS;

        MediaType mt = MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(filename));

        if (mt != null) {
            result = (byte) mt.getId();
        }

        if (returnTorrentsAsDocument && result == Constants.FILE_TYPE_TORRENTS) {
            result = Constants.FILE_TYPE_DOCUMENTS;
        }

        return result;
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
}
