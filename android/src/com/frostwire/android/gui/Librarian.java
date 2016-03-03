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

package com.frostwire.android.gui;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
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
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platforms;
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.*;

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

    private final Application context;
    private final FileCountCache[] cache; // it is an array for performance reasons

    private static Librarian instance;

    public synchronized static void create(Application context) {
        if (instance != null) {
            return;
        }
        instance = new Librarian(context);
    }

    public static Librarian instance() {
        if (instance == null) {
            throw new RuntimeException("Librarian not created");
        }
        return instance;
    }

    private Librarian(Application context) {
        this.context = context;
        this.cache = new FileCountCache[]{new FileCountCache(), new FileCountCache(), new FileCountCache(), new FileCountCache(), new FileCountCache(), new FileCountCache(), new FileCountCache()};
    }

    public List<FileDescriptor> getFiles(byte fileType, int offset, int pageSize) {
        return getFiles(offset, pageSize, TableFetchers.getFetcher(fileType));
    }

    public List<FileDescriptor> getFiles(byte fileType, String where, String[] whereArgs) {
        return getFiles(0, Integer.MAX_VALUE, TableFetchers.getFetcher(fileType), where, whereArgs);
    }

    /**
     * @param fileType
     * @return int
     */
    public int getNumFiles(byte fileType) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);

        if (cache[fileType].cacheValid()) {
            return cache[fileType].getCount();
        }

        Cursor c = null;

        int result;
        int numFiles = 0;

        try {
            ContentResolver cr = context.getContentResolver();
            c = cr.query(fetcher.getContentUri(), new String[]{BaseColumns._ID}, fetcher.where(), fetcher.whereArgs(), null);
            numFiles = c != null ? c.getCount() : 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get num of files", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }

        result = numFiles;

        cache[fileType].updateOnDisk(result);

        return result;
    }

    public FileDescriptor getFileDescriptor(byte fileType, int fileId) {
        List<FileDescriptor> fds = getFiles(0, 1, TableFetchers.getFetcher(fileType), BaseColumns._ID + "=?", new String[]{String.valueOf(fileId)});
        if (fds.size() > 0) {
            return fds.get(0);
        } else {
            return null;
        }
    }

    public String renameFile(FileDescriptor fd, String newFileName) {
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

    public void deleteFiles(byte fileType, Collection<FileDescriptor> fds, final Context context) {
        List<Integer> ids = new ArrayList<>(fds.size());
        final int audioMediaType = MediaType.getAudioMediaType().getId();
        for (FileDescriptor fd : fds) {
            if (new File(fd.filePath).delete()) {
                ids.add(fd.id);
                if (context != null && fileType == fd.fileType && fileType == audioMediaType) {
                    MusicUtils.removeSongFromAllPlaylists(context, fd.id);
                }
            }
        }

        try {
            if (context != null) {
                ContentResolver cr = context.getContentResolver();
                TableFetcher fetcher = TableFetchers.getFetcher(fileType);
                cr.delete(fetcher.getContentUri(), MediaColumns._ID + " IN " + buildSet(ids), null);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Failed to delete files from media store", e);
        }
        invalidateCountCache(fileType);
    }

    public void scan(File file) {
        scan(file, Transfers.getIgnorableFiles());
    }

    public void scan(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(uri);
        context.sendBroadcast(intent);
    }

    public Finger finger() {
        Finger finger = new Finger();

        finger.numTotalAudioFiles = getNumFiles(Constants.FILE_TYPE_AUDIO);
        finger.numTotalVideoFiles = getNumFiles(Constants.FILE_TYPE_VIDEOS);
        finger.numTotalPictureFiles = getNumFiles(Constants.FILE_TYPE_PICTURES);
        finger.numTotalDocumentFiles = getNumFiles(Constants.FILE_TYPE_DOCUMENTS);
        finger.numTotalTorrentFiles = getNumFiles(Constants.FILE_TYPE_TORRENTS);
        finger.numTotalRingtoneFiles = getNumFiles(Constants.FILE_TYPE_RINGTONES);

        return finger;
    }

    public void syncMediaStore() {
        if (!SystemUtils.isPrimaryExternalStorageMounted()) {
            return;
        }

        Thread t = new Thread(new Runnable() {
            public void run() {
                syncMediaStoreSupport();
            }
        });
        t.setName("syncMediaStore");
        t.setDaemon(true);
        t.start();
    }

    public EphemeralPlaylist createEphemeralPlaylist(FileDescriptor fd) {
        List<FileDescriptor> fds = Librarian.instance().getFiles(Constants.FILE_TYPE_AUDIO, FilenameUtils.getPath(fd.filePath), false);

        if (fds.size() == 0) { // just in case
            Log.w(TAG, "Logic error creating ephemeral playlist");
            fds.add(fd);
        }

        EphemeralPlaylist playlist = new EphemeralPlaylist(fds);
        playlist.setNextItem(new PlaylistItem(fd));

        return playlist;
    }

    public void invalidateCountCache() {
        for (FileCountCache c : cache) {
            if (c != null) {
                c.lastTimeCachedOnDisk = 0;
            }
        }
        //broadcastRefreshFinger();
    }

    /**
     * @param fileType
     */
    void invalidateCountCache(byte fileType) {
        cache[fileType].lastTimeCachedOnDisk = 0;
    }

    private void syncMediaStoreSupport() {
        Set<File> ignorableFiles = Transfers.getIgnorableFiles();

        syncMediaStore(Constants.FILE_TYPE_AUDIO, ignorableFiles);
        syncMediaStore(Constants.FILE_TYPE_PICTURES, ignorableFiles);
        syncMediaStore(Constants.FILE_TYPE_VIDEOS, ignorableFiles);
        syncMediaStore(Constants.FILE_TYPE_RINGTONES, ignorableFiles);
        syncMediaStore(Constants.FILE_TYPE_DOCUMENTS, ignorableFiles);

        scan(Platforms.torrents());
    }

    private void syncMediaStore(byte fileType, Set<File> ignorableFiles) {
        TableFetcher fetcher = TableFetchers.getFetcher(fileType);

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

            List<Integer> ids = new ArrayList<>();

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

    private List<FileDescriptor> getFiles(int offset, int pageSize, TableFetcher fetcher) {
        return getFiles(offset, pageSize, fetcher, null, null);
    }

    /**
     * Returns a list of Files.
     *
     * @param offset   - from where (starting at 0)
     * @param pageSize - how many results
     * @param fetcher  - An implementation of TableFetcher
     * @return List<FileDescriptor>
     */
    private List<FileDescriptor> getFiles(int offset, int pageSize, TableFetcher fetcher, String where, String[] whereArgs) {
        List<FileDescriptor> result = new ArrayList<>();
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
                if (!isInvalidDocument(fd)) {
                    result.add(fd);
                }
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

    /**
     * @param fd
     * @return true if file is catalogued as a Document and has no .extension.
     */
    public static boolean isInvalidDocument(FileDescriptor fd) {
        return fd.fileType == Constants.FILE_TYPE_DOCUMENTS && StringUtils.isNullOrEmpty(FilenameUtils.getExtension(fd.filePath));
    }

    public List<FileDescriptor> getFiles(String filepath, boolean exactPathMatch) {
        return getFiles(getFileType(filepath, true), filepath, exactPathMatch);
    }

    /**
     * @param fileType
     * @param filepath
     * @param exactPathMatch - set it to false and pass an incomplete filepath prefix to get files in a folder for example.
     * @return
     */
    public List<FileDescriptor> getFiles(byte fileType, String filepath, boolean exactPathMatch) {
        String where = MediaColumns.DATA + " LIKE ?";
        String[] whereArgs = new String[]{(exactPathMatch) ? filepath : "%" + filepath + "%"};
        List<FileDescriptor> fds = Librarian.instance().getFiles(fileType, where, whereArgs);
        return fds;
    }

    private void scan(File file, Set<File> ignorableFiles) {
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

    /** Given a folder path it'll return all the files contained within it and it's subfolders
     * as a flat set of Files.
     *
     * Non-recursive implementation, up to 20% faster in tests than recursive implementation. :)
     *
     * @author gubatron
     * @param folder
     * @param extensions If you only need certain files filtered by their extensions, use this string array (without the "."). or set to null if you want all files. e.g. ["txt","jpg"] if you only want text files and jpegs.
     *
     * @return The set of files.
     */
    private static Collection<File> getAllFolderFiles(File folder, String[] extensions) {
        Set<File> results = new HashSet<File>();
        Stack<File> subFolders = new Stack<File>();
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

    /**
     * When a file descriptor's URI does not fall into one of the TableFetchers (ContentProviders)
     * the FileDescriptor object obtained will come from TableFetchers.DocumentsTableFetcher and
     * it will NOT have a `filePath` attribute set (null), also the `fileType` field will be set to
     * Constants.FILE_TYPE_DOCUMENTS, which can throw things off if the given URI is not as expected
     * even though the file may be a media file.
     * <p/>
     * This method will use the given URI on the generic content resolver to find the disk file path,
     * update the fileDescriptor.filePath field, and then with the extension it will try to determine
     * the closest fileType (byte) associated.
     *
     * @param uri            (input)
     * @param fileDescriptor (output, can't be null)
     */
    public void updateFileDescriptor(Uri uri, FileDescriptor fileDescriptor) {
        if (fileDescriptor.filePath == null) {
            try {
                Cursor query = context.getContentResolver().query(uri, null, null, null, null);
                int pathColumn = query.getColumnIndex("_data");
                query.moveToFirst();
                fileDescriptor.filePath = query.getString(pathColumn);
                fileDescriptor.fileType = (byte) MediaType.getMediaTypeForExtension(FilenameUtils.getExtension(fileDescriptor.filePath)).getId();
            } catch (Throwable t) {
            }
        }
    }

    private static class FileCountCache {

        public int onDisk;
        public long lastTimeCachedOnDisk;

        public FileCountCache() {
            onDisk = 0;
            lastTimeCachedOnDisk = 0;
        }

        public void updateOnDisk(int num) {
            onDisk = num;
            lastTimeCachedOnDisk = System.currentTimeMillis();
        }

        public int getCount() {
            return onDisk;
        }

        public boolean cacheValid() {
            long delta = System.currentTimeMillis() - lastTimeCachedOnDisk;
            return delta < Constants.LIBRARIAN_FILE_COUNT_CACHE_TIMEOUT;
        }
    }

    private FileDescriptor getFileDescriptor(File f) {
        FileDescriptor fd = null;
        if (f.exists()) {
            List<FileDescriptor> files = getFiles(f.getAbsolutePath(), false);
            if (!files.isEmpty()) {
                fd = files.get(0);
            }
        }
        return fd;
    }

    public FileDescriptor getFileDescriptor(Uri uri) {
        FileDescriptor fd = null;
        try {
            if (uri != null) {
                if (uri.toString().startsWith("file://")) {
                    fd = getFileDescriptor(new File(uri.getPath()));
                } else {
                    TableFetcher fetcher = TableFetchers.getFetcher(uri);
                    byte fileType = fetcher.getFileType();
                    int id = Integer.valueOf(uri.getLastPathSegment());
                    fd = getFileDescriptor(fileType, id);
                }
            }
        } catch (Throwable e) {
            fd = null;
            // sometimes uri.getLastPathSegment() is not an integer
            e.printStackTrace();
        }

        // try to save it.
        if (fd == null && uri != null) {
            fd = new FileDescriptor();
            if (uri.toString().startsWith("content://")) {
                fd.id = Integer.valueOf(uri.getLastPathSegment());
                updateFileDescriptor(uri, fd);
            } else if (uri.toString().startsWith("file://")) {
                fd.filePath = uri.toString();
                final String extension = FilenameUtils.getExtension(fd.filePath);
                final MediaType mediaTypeForExtension = MediaType.getMediaTypeForExtension(extension);

                if (mediaTypeForExtension != null) {
                    fd.fileType = (byte) mediaTypeForExtension.getId();
                } else {
                    // set the file type to be a document if we don't know the given extension
                    // we were having a NPE here.
                    fd.fileType = (byte) MediaType.getDocumentMediaType().getId();
                }
            }
        }
        return fd;
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
