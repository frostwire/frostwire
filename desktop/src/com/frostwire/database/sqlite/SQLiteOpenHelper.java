/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.database.sqlite;

import com.frostwire.content.Context;
import com.frostwire.util.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

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
        for (File file : directory.listFiles()) {
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
            sb.append("jdbc:h2:");
            folderpath = dbpath + "." + version;
            String fullpath = folderpath + File.separator + name;
            sb.append(fullpath);
            if (extraArgs != null) {
                sb.append(";").append(extraArgs);
            }
            boolean create = !(new File(folderpath).exists());
            Connection connection = DriverManager.getConnection(sb.toString(), "SA", "");
            SQLiteDatabase db = new SQLiteDatabase(fullpath, connection);
            if (create) {
                onCreate(db);
            }
            return db;
        } catch (Throwable e) {
            LOG.error("Error opening the database", e);
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
