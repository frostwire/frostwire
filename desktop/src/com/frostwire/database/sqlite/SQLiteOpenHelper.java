/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.database.sqlite;

import com.frostwire.content.Context;
import com.frostwire.util.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Objects;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SQLiteOpenHelper {
    private static final Logger LOG = Logger.getLogger(SQLiteOpenHelper.class);
    private final SQLiteDatabase db;
    private String folderpath;

    protected SQLiteOpenHelper(Context context, String name, int version, String extraArgs) {
        String dbpath = context.getDatabasePath(name).getAbsolutePath();
        db = openDatabase(dbpath, name, version, extraArgs);
    }

    private static long folderSize(File directory) {
        long length = 0;
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isFile())
                length += file.length();
            else
                length += folderSize(file);
        }
        return length;
    }

    public synchronized SQLiteDatabase getWritableDatabase() {
        return db;
    }

    public synchronized SQLiteDatabase getReadableDatabase() {
        return db;
    }

    /**
     * Called when the database is created for the first time. This is where the
     * creation of tables and the initial population of the tables should happen.
     *
     * @param db The database.
     */
    protected abstract void onCreate(SQLiteDatabase db);

    /**
     * Called when the database needs to be upgraded. The implementation
     * should use this method to drop tables, add tables, or do anything else it
     * needs to upgrade to the new schema version.
     *
     * <p>The SQLite ALTER TABLE documentation can be found
     * <a href="http://sqlite.org/lang_altertable.html">here</a>. If you add new columns
     * you can use ALTER TABLE to insert them into a live table. If you rename or remove columns
     * you can use ALTER TABLE to rename the old table, then create the new table and then
     * populate the new table with the contents of the old table.
     *
     * @param db         The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    @SuppressWarnings("unused")
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    private SQLiteDatabase openDatabase(String dbpath, String name, int version, String extraArgs) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("jdbc:sqlite:");
            folderpath = dbpath + "." + version;
            String fullpath = folderpath + File.separator + name;
            sb.append(fullpath);
            if (extraArgs != null) {
                sb.append(";").append(extraArgs);
            }
            boolean dbFolderExists = new File(folderpath).exists();
            boolean dbFileExists = new File(fullpath).exists();
            boolean create =  !dbFolderExists || !dbFileExists;
            if (create) {
                if (!dbFolderExists) {
                    if (!new File(folderpath).mkdirs()) {
                        LOG.error("SQLiteOpenHelper.openDatabase(): Error creating database folder: " + folderpath);
                        throw new RuntimeException("SQLiteOpenHelper.openDatabase(): Could not create database folder: " + folderpath);
                    }
                }
                if (!dbFileExists) {
                    try {
                        new File(fullpath).createNewFile();
                    } catch (Throwable e) {
                        LOG.error("SQLiteOpenHelper.openDatabase(): Error creating database file: " + fullpath, e);
                        throw new RuntimeException("SQLiteOpenHelper.openDatabase(): Could not create database file: " + fullpath, e);
                    }
                }
            }
            Connection connection = DriverManager.getConnection(sb.toString(), "SA", "");
            SQLiteDatabase db = new SQLiteDatabase(fullpath, connection);
            if (create) {
                onCreate(db);
            }
            return db;
        } catch (Throwable e) {
            LOG.error("SQLiteOpenHelper.openDatabase(): Error opening the database", e);
            throw new RuntimeException(e);
        }
    }

    public long sizeInBytes() {
        if (folderpath != null) {
            File dbFolder = new File(folderpath);
            if (dbFolder.exists() && dbFolder.isDirectory()) {
                return folderSize(dbFolder);
            }
        }
        return 0;
    }
}
