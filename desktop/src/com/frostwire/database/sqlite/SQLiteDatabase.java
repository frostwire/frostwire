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

import com.frostwire.content.ContentValues;
import com.frostwire.database.Cursor;
import com.frostwire.database.SQLException;
import com.frostwire.util.Logger;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author gubatron
 * @author aldenml
 */
public class SQLiteDatabase {
    private static final Logger LOG = Logger.getLogger(SQLiteDatabase.class);

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private final Connection connection;
    private final AtomicBoolean open = new AtomicBoolean(false);
    private String path;

    SQLiteDatabase(String path, Connection connection) {
        this.path = path;
        this.connection = connection;
        open.set(true);
    }

    /**
     * Getter for the path to the database file.
     *
     * @return the path to our database file.
     */
    private String getPath() {
        return path;
    }

    /**
     * Runs the provided SQL and returns a cursor over the result set.
     *
     * @param sql           the SQL query. The SQL string must not be ; terminated
     * @param selectionArgs You may include ?s in where clause in the query,
     *                      which will be replaced by the values from selectionArgs. The
     *                      values will be bound as Strings.
     * @return A {@link Cursor} object, which is positioned before the first entry. Note that
     * {@link Cursor}s are not synchronized, see the documentation for more details.
     */
    Cursor rawQueryWithFactory(String sql, String[] selectionArgs) {
        verifyDbIsOpen();
        Cursor cursor = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            synchronized (connection) {
                statement = prepareStatement(connection, sql, selectionArgs);
                resultSet = statement.executeQuery();
                return new Cursor(statement, resultSet);
            }
        } catch (Throwable e) {
            LOG.warn("Error performing SQL statement: " + sql, e);
        }
        return cursor;
    }

    /**
     * Execute a single SQL statement that is NOT a SELECT
     * or any other SQL statement that returns data.
     * <p>
     * It has no means to return any data (such as the number of affected rows).
     * Instead, you're encouraged to use {@link #insert(String, String, ContentValues)},
     * {@link #update(String, ContentValues, String, String[])}, et al, when possible.
     * </p>*
     *
     * @param sql the SQL statement to be executed. Multiple statements separated by semicolons are
     *            not supported.
     * @throws SQLException if the SQL string is invalid
     */
    public void execSQL(String sql) throws SQLException {
        executeSql(sql, null);
    }

    /**
     * Convenience method for inserting a row into the database.
     *
     * @param table          the table to insert the row into
     * @param nullColumnHack optional; may be <code>null</code>.
     *                       SQL doesn't allow inserting a completely empty row without
     *                       naming at least one column name.  If your provided <code>values</code> is
     *                       empty, no column names are known and an empty row can't be inserted.
     *                       If not set to null, the <code>nullColumnHack</code> parameter
     *                       provides the name of nullable column name to explicitly insert a NULL into
     *                       in the case where your <code>values</code> is empty.
     * @param values         this map contains the initial column values for the
     *                       row. The keys should be the column names and the values the
     *                       column values
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     */
    public long insert(String table, String nullColumnHack, ContentValues values) {
        verifyDbIsOpen();
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT");
        sql.append(" INTO ");
        sql.append(table);
        sql.append(" (");
        Object[] bindArgs = null;
        int size = (values != null && values.size() > 0) ? values.size() : 0;
        if (size > 0) {
            bindArgs = new Object[size];
            int i = 0;
            for (String colName : values.keySet()) {
                sql.append((i > 0) ? "," : "");
                sql.append(colName);
                bindArgs[i++] = values.get(colName);
            }
            sql.append(')');
            sql.append(" VALUES (");
            for (i = 0; i < size; i++) {
                sql.append((i > 0) ? ",?" : "?");
            }
        } else {
            sql.append(nullColumnHack).append(") VALUES (NULL");
        }
        sql.append(')');
        return executeSql(sql.toString(), bindArgs);
    }

    /**
     * Convenience method for deleting rows in the database.
     *
     * @param table       the table to delete from
     * @param whereClause the optional WHERE clause to apply when deleting.
     *                    Passing null will delete all rows.
     * @return the number of rows affected if a whereClause is passed in, 0
     * otherwise. To remove all rows and get a count pass "1" as the
     * whereClause.
     */
    public int delete(String table, String whereClause, String[] whereArgs) {
        verifyDbIsOpen();
        String sql = "DELETE FROM " + table + (!StringUtils.isEmpty(whereClause) ? " WHERE " + whereClause : "");
        return executeSql(sql, whereArgs);
    }

    /**
     * Convenience method for updating rows in the database.
     *
     * @param table       the table to update in
     * @param values      a map from column names to new column values. null is a
     *                    valid value that will be translated to NULL.
     * @param whereClause the optional WHERE clause to apply when updating.
     *                    Passing null will update all rows.
     * @return the number of rows affected
     */
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        verifyDbIsOpen();
        if (values == null || values.size() == 0) {
            throw new IllegalArgumentException("Empty values");
        }
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(table);
        sql.append(" SET ");
        // move all bind args to one array
        int setValuesSize = values.size();
        int bindArgsSize = (whereArgs == null) ? setValuesSize : (setValuesSize + whereArgs.length);
        Object[] bindArgs = new Object[bindArgsSize];
        int i = 0;
        for (String colName : values.keySet()) {
            sql.append((i > 0) ? "," : "");
            sql.append(colName);
            bindArgs[i++] = values.get(colName);
            sql.append("=?");
        }
        if (whereArgs != null) {
            for (i = setValuesSize; i < bindArgsSize; i++) {
                bindArgs[i] = whereArgs[i - setValuesSize];
            }
        }
        if (!StringUtils.isEmpty(whereClause)) {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }
        return executeSql(sql.toString(), bindArgs);
    }

    /**
     * @return true if the DB is currently open (has not been closed)
     */
    private boolean isOpen() {
        return open.get();
    }

    public void close() {
        if (open.compareAndSet(true, false)) {
            try {
                Statement statement = connection.createStatement();
                statement.execute("SHUTDOWN");
                connection.close();
            } catch (Throwable e) {
                LOG.warn("Error closing the smart search database", e);
            }
        }
    }

    private int executeSql(String sql, Object[] bindArgs) throws SQLException {
        PreparedStatement statement = null;
        try {
            synchronized (connection) {
                statement = prepareStatement(connection, sql, bindArgs);
                return statement.executeUpdate();
            }
        } catch (Throwable e) {
            LOG.warn("Error performing SQL statement: " + sql, e);
            return -1;
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void verifyDbIsOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("database " + getPath() + " already closed");
        }
    }

    private PreparedStatement prepareStatement(Connection connection, String sql, Object... arguments) throws Exception {
        PreparedStatement statement = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
        if (arguments != null) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
        }
        return statement;
    }
}
