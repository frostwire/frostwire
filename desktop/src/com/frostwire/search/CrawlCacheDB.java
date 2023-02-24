/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.frostwire.search;

import com.frostwire.content.ContentValues;
import com.frostwire.content.Context;
import com.frostwire.database.Cursor;
import com.frostwire.database.sqlite.SQLiteDatabase;
import com.frostwire.database.sqlite.SQLiteOpenHelper;
import com.frostwire.database.sqlite.SQLiteQueryBuilder;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.SearchSettings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public final class CrawlCacheDB {
    private static final Logger LOG = Logger.getLogger(CrawlCacheDB.class);
    private static final String DATABASE_NAME = "crawldb";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_NAME = "cache_data";
    private static final String DEFAULT_SORT_ORDER = Columns.DATE_ADDED + " DESC";
    private final static CrawlCacheDB instance = new CrawlCacheDB();

    private final DatabaseHelper databaseHelper;

    private CrawlCacheDB() {
        databaseHelper = new DatabaseHelper(new Context());
    }

    public static CrawlCacheDB instance() {
        return instance;
    }

    public Cursor query(String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);
        // If no sort order is specified use the default
        String orderBy;
        if (StringUtils.isEmpty(sortOrder)) {
            orderBy = DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }
        // Get the database and run the query
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        return qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
    }

    public long insert(ContentValues initialValues) {
        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }
        Long now = System.currentTimeMillis() / 1000;
        if (!values.containsKey(Columns.DATE_ADDED)) {
            values.put(Columns.DATE_ADDED, now);
        }
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.insert(TABLE_NAME, "", values);
    }

    public int delete(String where, String[] whereArgs) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.delete(TABLE_NAME, where, whereArgs);
    }

    void truncate() {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        //SQLite does not have an explicit TRUNCATE TABLE command like other databases.
        // Instead, it has added a TRUNCATE optimizer to the DELETE statement.
        // To truncate a table in SQLite, you just need to execute a DELETE statement without a WHERE clause.
        // The TRUNCATE optimizer handles the rest.
        db.execSQL("DELETE FROM " + TABLE_NAME);
    }

    @SuppressWarnings("unused")
    public int update(ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.update(TABLE_NAME, values, where, whereArgs);
    }

    long sizeInBytes() {
        return databaseHelper.sizeInBytes();
    }

    public static final class Columns {
        public static final String ID = "id";
        public static final String DATA = "data";
        static final String KEY = "key";
        static final String DATE_ADDED = "date_added";

        private Columns() {
        }
    }

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        // 4MB cache size and scan-resistant cache algorithm "Two Queue" (2Q) with second level soft reference
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, DATABASE_VERSION, null);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (SearchSettings.SMART_SEARCH_DATABASE_FOLDER.getValue().exists()) {
                try {
                    FileUtils.deleteDirectory(SearchSettings.SMART_SEARCH_DATABASE_FOLDER.getValue());
                } catch (IOException e) {
                    LOG.warn("Unable to delete old smart search database");
                }
            }
            db.execSQL("CREATE TABLE " + TABLE_NAME + " (" + Columns.ID + " INTEGER PRIMARY KEY," + Columns.KEY + " VARCHAR," + Columns.DATA + " BLOB," + Columns.DATE_ADDED + " INTEGER" + ");");
            db.execSQL("CREATE INDEX idx_" + TABLE_NAME + "_" + Columns.ID + " ON " + TABLE_NAME + " (" + Columns.ID + ")");
            db.execSQL("CREATE INDEX idx_" + TABLE_NAME + "_" + Columns.KEY + " ON " + TABLE_NAME + " (" + Columns.KEY + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            LOG.warn("Upgrading documents database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }
}
