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

package com.frostwire.android.core.providers;

import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video.VideoColumns;

import androidx.annotation.RequiresApi;

import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.MediaType;
import com.frostwire.android.util.SystemUtils;

import static android.provider.MediaStore.Audio.AudioColumns.ALBUM;
import static android.provider.MediaStore.Audio.AudioColumns.ALBUM_ID;
import static android.provider.MediaStore.Audio.AudioColumns.ARTIST;
import static android.provider.MediaStore.Audio.AudioColumns.DATA;
import static android.provider.MediaStore.Audio.AudioColumns.DATE_ADDED;
import static android.provider.MediaStore.Audio.AudioColumns.DATE_MODIFIED;
import static android.provider.MediaStore.Audio.AudioColumns.MIME_TYPE;
import static android.provider.MediaStore.Audio.AudioColumns.SIZE;
import static android.provider.MediaStore.Audio.AudioColumns.TITLE;
import static android.provider.MediaStore.Audio.AudioColumns.YEAR;
import static android.provider.MediaStore.Audio.AudioColumns._ID;

/**
 * Help yourself with TableFetchers.
 * <p/>
 * Note: if you need to fetch files by file path(s) see Librarian.instance().getFiles(filepath,exactMatch)
 *
 * @author gubatron
 * @author aldenml
 */
public final class TableFetchers {

    private static final TableFetcher AUDIO_TABLE_FETCHER = new AudioTableFetcher();
    private static final TableFetcher PICTURES_TABLE_FETCHER = new PicturesTableFetcher();
    private static final TableFetcher VIDEOS_TABLE_FETCHER = new VideosTableFetcher();
    private static final TableFetcher DOCUMENTS_TABLE_FETCHER = new DocumentsTableFetcher();
    private static final TableFetcher RINGTONES_TABLE_FETCHER = new RingtonesTableFetcher();
    private static final TableFetcher TORRENTS_TABLE_FETCHER = new TorrentsTableFetcher();

    public static abstract class AbstractTableFetcher implements TableFetcher {

        @Override
        public String where() {
            return null;
        }

        @Override
        public String[] whereArgs() {
            return new String[0];
        }
    }

    /**
     * Default Table Fetcher for Audio Files.
     */
    public static class AudioTableFetcher extends AbstractTableFetcher {

        protected int idCol;
        protected int pathCol;
        protected int mimeCol;
        protected int artistCol;
        protected int titleCol;
        protected int albumCol;
        protected int yearCol;
        protected int sizeCol;
        protected int dateAddedCol;
        protected int dateModifiedCol;
        protected int albumIdCol;

        @RequiresApi(api = Build.VERSION_CODES.R)
        public String[] getColumns() {
            return new String[]{_ID, ARTIST, TITLE, ALBUM, DATA, YEAR, MIME_TYPE, SIZE, DATE_ADDED, DATE_MODIFIED, ALBUM_ID};
        }

        public String getSortByExpression() {
            return DATE_ADDED + " DESC";
        }

        public Uri getExternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        @Override
        public Uri getInternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return Uri.parse("content://com.frostwire.android.fileprovider");
                // MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL);
            }
            return MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        public void prepare(Cursor cur) {
            idCol = cur.getColumnIndex(_ID);
            pathCol = cur.getColumnIndex(DATA);
            mimeCol = cur.getColumnIndex(MIME_TYPE);
            artistCol = cur.getColumnIndex(ARTIST);
            titleCol = cur.getColumnIndex(TITLE);
            albumCol = cur.getColumnIndex(ALBUM);
            yearCol = cur.getColumnIndex(YEAR);
            sizeCol = cur.getColumnIndex(SIZE);
            dateAddedCol = cur.getColumnIndex(DATE_ADDED);
            dateModifiedCol = cur.getColumnIndex(DATE_MODIFIED);
            albumIdCol = cur.getColumnIndex(ALBUM_ID);
        }

        public FWFileDescriptor fetch(Cursor cur) {
            int id = cur.getInt(idCol);
            String path = cur.getString(pathCol);
            String mime = cur.getString(mimeCol);
            String artist = cur.getString(artistCol);
            String title = cur.getString(titleCol);
            String album = cur.getString(albumCol);
            String year = cur.getString(yearCol);
            int size = cur.getInt(sizeCol);
            long dateAdded = cur.getLong(dateAddedCol);
            long dateModified = cur.getLong(dateModifiedCol);
            long albumId = cur.getLong(albumIdCol);

            FWFileDescriptor fd = new FWFileDescriptor(id, artist, title, album, year, path, Constants.FILE_TYPE_AUDIO, mime, size, dateAdded, dateModified, true);
            fd.albumId = albumId;

            return fd;
        }

        public byte getFileType() {
            return Constants.FILE_TYPE_AUDIO;
        }
    }

    public static class PicturesTableFetcher extends AbstractTableFetcher {

        private int idCol;
        private int titleCol;
        private int pathCol;
        private int mimeCol;
        private int sizeCol;
        private int dateAddedCol;
        private int dateModifiedCol;

        public FWFileDescriptor fetch(Cursor cur) {
            int id = cur.getInt(idCol);
            String path = cur.getString(pathCol);
            String mime = cur.getString(mimeCol);
            String title = cur.getString(titleCol);
            int size = cur.getInt(sizeCol);
            long dateAdded = cur.getLong(dateAddedCol);
            long dateModified = cur.getLong(dateModifiedCol);

            return new FWFileDescriptor(id, null, title, null, null, path, Constants.FILE_TYPE_PICTURES, mime, size, dateAdded, dateModified, true);
        }

        public String[] getColumns() {
            return new String[]{ImageColumns._ID, ImageColumns.TITLE, ImageColumns.DATA, ImageColumns.MIME_TYPE, ImageColumns.MINI_THUMB_MAGIC, ImageColumns.SIZE, ImageColumns.DATE_ADDED, ImageColumns.DATE_MODIFIED};
        }

        public Uri getExternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        @Override
        public Uri getInternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_INTERNAL);
            }
            return MediaStore.Images.Media.INTERNAL_CONTENT_URI;
        }

        public byte getFileType() {
            return Constants.FILE_TYPE_PICTURES;
        }

        public String getSortByExpression() {
            return ImageColumns.DATE_ADDED + " DESC";
        }

        public void prepare(Cursor cur) {
            idCol = cur.getColumnIndex(ImageColumns._ID);
            titleCol = cur.getColumnIndex(ImageColumns.TITLE);
            pathCol = cur.getColumnIndex(ImageColumns.DATA);
            mimeCol = cur.getColumnIndex(ImageColumns.MIME_TYPE);
            sizeCol = cur.getColumnIndex(ImageColumns.SIZE);
            dateAddedCol = cur.getColumnIndex(ImageColumns.DATE_ADDED);
            dateModifiedCol = cur.getColumnIndex(ImageColumns.DATE_MODIFIED);
        }
    }

    public static final class VideosTableFetcher extends AbstractTableFetcher {

        private int idCol;
        private int pathCol;
        private int mimeCol;
        private int artistCol;
        private int titleCol;
        private int albumCol;
        private int sizeCol;
        private int dateAddedCol;
        private int dateModifiedCol;

        public FWFileDescriptor fetch(Cursor cur) {
            int id = cur.getInt(idCol);
            String path = cur.getString(pathCol);
            String mime = cur.getString(mimeCol);
            String artist = cur.getString(artistCol);
            String title = cur.getString(titleCol);
            String album = cur.getString(albumCol);
            int size = cur.getInt(sizeCol);
            long dateAdded = cur.getLong(dateAddedCol);
            long dateModified = cur.getLong(dateModifiedCol);

            return new FWFileDescriptor(id, artist, title, album, null, path, Constants.FILE_TYPE_VIDEOS, mime, size, dateAdded, dateModified, true);
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        public String[] getColumns() {
            return new String[]{VideoColumns._ID, VideoColumns.ARTIST, VideoColumns.TITLE, VideoColumns.ALBUM, VideoColumns.DATA, VideoColumns.MIME_TYPE, VideoColumns.MINI_THUMB_MAGIC, VideoColumns.SIZE, VideoColumns.DATE_ADDED, VideoColumns.DATE_MODIFIED};
        }

        public Uri getExternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        @Override
        public Uri getInternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_INTERNAL);
            }
            return MediaStore.Video.Media.INTERNAL_CONTENT_URI;
        }

        public byte getFileType() {
            return Constants.FILE_TYPE_VIDEOS;
        }

        public String getSortByExpression() {
            return VideoColumns.DATE_ADDED + " DESC";
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        public void prepare(Cursor cur) {
            idCol = cur.getColumnIndex(VideoColumns._ID);
            pathCol = cur.getColumnIndex(VideoColumns.DATA);
            mimeCol = cur.getColumnIndex(VideoColumns.MIME_TYPE);
            artistCol = cur.getColumnIndex(VideoColumns.ARTIST);
            titleCol = cur.getColumnIndex(VideoColumns.TITLE);
            albumCol = cur.getColumnIndex(VideoColumns.ALBUM);
            sizeCol = cur.getColumnIndex(VideoColumns.SIZE);
            dateAddedCol = cur.getColumnIndex(VideoColumns.DATE_ADDED);
            dateModifiedCol = cur.getColumnIndex(VideoColumns.DATE_MODIFIED);
        }
    }

    public static abstract class AbstractFilesTableFetcher extends AbstractTableFetcher {
        private int idCol;
        private int pathCol;
        private int mimeCol;
        private int titleCol;
        private int sizeCol;
        private int dateAddedCol;
        private int dateModifiedCol;

        public FWFileDescriptor fetch(Cursor cur) {
            int id = cur.getInt(idCol);
            String path = cur.getString(pathCol);
            String mime = cur.getString(mimeCol);
            String title = cur.getString(titleCol);
            int size = cur.getInt(sizeCol);
            long dateAdded = cur.getLong(dateAddedCol);
            long dateModified = cur.getLong(dateModifiedCol);
            return new FWFileDescriptor(id, null, title, null, null, path, Constants.FILE_TYPE_DOCUMENTS, mime, size, dateAdded, dateModified, true);
        }

        public String[] getColumns() {
            return new String[]{FileColumns._ID, FileColumns.DATA, FileColumns.SIZE, FileColumns.TITLE, FileColumns.MIME_TYPE, FileColumns.DATE_ADDED, FileColumns.DATE_MODIFIED};
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        public Uri getExternalContentUri() {
            if (SystemUtils.hasAndroid10OrNewer()) {
                return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            }
            return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }

        @RequiresApi(api = Build.VERSION_CODES.Q)
        public Uri getInternalContentUri() {
            return MediaStore.Files.getContentUri(MediaStore.VOLUME_INTERNAL);
        }

        public byte getFileType() {
            return Constants.FILE_TYPE_DOCUMENTS;
        }

        public String getSortByExpression() {
            return FileColumns.DATE_ADDED + " DESC";
        }

        public void prepare(Cursor cur) {
            idCol = cur.getColumnIndex(FileColumns._ID);
            pathCol = cur.getColumnIndex(FileColumns.DATA);
            mimeCol = cur.getColumnIndex(FileColumns.MIME_TYPE);
            titleCol = cur.getColumnIndex(FileColumns.TITLE);
            sizeCol = cur.getColumnIndex(FileColumns.SIZE);
            dateAddedCol = cur.getColumnIndex(FileColumns.DATE_ADDED);
            dateModifiedCol = cur.getColumnIndex(FileColumns.DATE_MODIFIED);
        }
    }

    public static final class DocumentsTableFetcher extends AbstractFilesTableFetcher {

        final static String extensionsWhereSubClause = getExtsWhereSubClause();

        private static String getExtsWhereSubClause() {
            final String[] exts = MediaType.getDocumentMediaType().getExtensions().toArray(new String[0]);
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            int index = 0;
            while (index < exts.length) {
                sb.append(FileColumns.DATA);
                sb.append(" LIKE '%.");
                sb.append(exts[index]);
                sb.append('\'');
                if (index < exts.length - 1) {
                    sb.append(" OR ");
                }
                index++;
            }
            sb.append(") AND ");
            return sb.toString();
        }

        @Override
        public String where() {
            return FileColumns.DATA + " NOT LIKE ? AND " +
                    FileColumns.DATA + " NOT LIKE ? AND " +
                    FileColumns.DATA + " NOT LIKE ? AND " +
                    FileColumns.DATA + " NOT LIKE ? AND " +
                    extensionsWhereSubClause +
                    FileColumns.MEDIA_TYPE + " = " + FileColumns.MEDIA_TYPE_NONE + " AND " +
                    FileColumns.SIZE + " > 0 AND " + FileColumns.SIZE + " != 4096";
        }

        @Override
        public String[] whereArgs() {
            return new String[]{"%cache%", "%/.%", "%/libtorrent/%", "%com.google.%"};
        }
    }

    public static final class TorrentsTableFetcher extends AbstractFilesTableFetcher {

        @Override
        public String where() {
            return FileColumns.DATA + " NOT LIKE ? AND " +
                    FileColumns.DATA + " NOT LIKE ? AND " +
                    FileColumns.DATA + " NOT LIKE ? AND " +
                    FileColumns.DATA + " LIKE ? AND " +
                    FileColumns.MEDIA_TYPE + " = " + FileColumns.MEDIA_TYPE_NONE + " AND " +
                    FileColumns.SIZE + " > 0";
        }

        @Override
        public String[] whereArgs() {
            return new String[]{"%/cache/%", "%/.%", "%/libtorrent/%", "%.torrent"};
        }
    }

    public static final class RingtonesTableFetcher extends AbstractTableFetcher {
        private int idCol;
        private int pathCol;
        private int mimeCol;
        private int artistCol;
        private int titleCol;
        private int albumCol;
        private int yearCol;
        private int sizeCol;
        private int dateAddedCol;
        private int dateModifiedCol;

        public FWFileDescriptor fetch(Cursor cur) {
            int id = cur.getInt(idCol);
            String path = cur.getString(pathCol);
            String mime = cur.getString(mimeCol);
            String artist = cur.getString(artistCol);
            String title = cur.getString(titleCol);
            String album = cur.getString(albumCol);
            String year = cur.getString(yearCol);
            int size = cur.getInt(sizeCol);
            long dateAdded = cur.getLong(dateAddedCol);
            long dateModified = cur.getLong(dateModifiedCol);

            return new FWFileDescriptor(id, artist, title, album, year, path, Constants.FILE_TYPE_RINGTONES, mime, size, dateAdded, dateModified, true);
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        public String[] getColumns() {
            return new String[]{_ID, ARTIST, TITLE, ALBUM, DATA, YEAR, MIME_TYPE, SIZE, DATE_ADDED, DATE_MODIFIED};
        }

        public Uri getExternalContentUri() {
            return null;
        }

        public Uri getInternalContentUri() {
            return MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
        }

        public byte getFileType() {
            return Constants.FILE_TYPE_RINGTONES;
        }

        public String getSortByExpression() {
            return DATE_ADDED + " DESC";
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        public void prepare(Cursor cur) {
            idCol = cur.getColumnIndex(_ID);
            pathCol = cur.getColumnIndex(DATA);
            mimeCol = cur.getColumnIndex(MIME_TYPE);
            artistCol = cur.getColumnIndex(ARTIST);
            titleCol = cur.getColumnIndex(TITLE);
            albumCol = cur.getColumnIndex(ALBUM);
            yearCol = cur.getColumnIndex(YEAR);
            sizeCol = cur.getColumnIndex(SIZE);
            dateAddedCol = cur.getColumnIndex(DATE_ADDED);
            dateModifiedCol = cur.getColumnIndex(DATE_MODIFIED);
        }
    }

    public static TableFetcher getFetcher(byte fileType) {
        switch (fileType) {
            case Constants.FILE_TYPE_AUDIO:
                return AUDIO_TABLE_FETCHER;
            case Constants.FILE_TYPE_PICTURES:
                return PICTURES_TABLE_FETCHER;
            case Constants.FILE_TYPE_VIDEOS:
                return VIDEOS_TABLE_FETCHER;
            case Constants.FILE_TYPE_DOCUMENTS:
                return DOCUMENTS_TABLE_FETCHER;
            case Constants.FILE_TYPE_RINGTONES:
                return RINGTONES_TABLE_FETCHER;
            case Constants.FILE_TYPE_TORRENTS:
                return TORRENTS_TABLE_FETCHER;
            default:
                return null;
        }
    }
}
