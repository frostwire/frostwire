/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.database;

import com.frostwire.util.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author gubatron
 * @author aldenml
 */
public class Cursor {
    private static final Logger LOG = Logger.getLogger(Cursor.class);
    private final Statement statement;
    private final ResultSet rs;

    public Cursor(Statement statement, ResultSet rs) {
        this.statement = statement;
        this.rs = rs;
    }

    /**
     * Returns the value of the requested column as an int.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null, the column type is not an integral type, or the
     * integer value is outside the range [<code>Integer.MIN_VALUE</code>,
     * <code>Integer.MAX_VALUE</code>] is implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as an int.
     */
    public int getInt(int columnIndex) {
        try {
            return rs.getInt(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return 0;
    }

    /**
     * Returns the value of the requested column as a String.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null or the column type is not a string type is
     * implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as a String.
     */
    @SuppressWarnings("unused")
    public String getString(int columnIndex) {
        try {
            return rs.getString(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return null;
    }

    /**
     * Returns the value of the requested column as a long.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null, the column type is not an integral type, or the
     * integer value is outside the range [<code>Long.MIN_VALUE</code>,
     * <code>Long.MAX_VALUE</code>] is implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as a long.
     */
    public long getLong(int columnIndex) {
        try {
            return rs.getLong(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return 0;
    }

    /**
     * Returns the zero-based index for the given column name, or -1 if the column doesn't exist.
     * If you expect the column to exist use getColumnIndexOrThrow(String) instead, which
     * will make the error more clear.
     *
     * @param columnName the name of the target column.
     * @return the zero-based column index for the given column name, or -1 if
     * the column name does not exist.
     */
    public int getColumnIndex(String columnName) {
        try {
            return rs.findColumn(columnName);
        } catch (SQLException e) {
            //LOG.warn("Error getting column index for name: " + columnName, e);
        }
        return -1;
    }

    /**
     * Returns the numbers of rows in the cursor.
     *
     * @return the number of rows in the cursor.
     */
    public int getCount() {
        try {
            rs.last();
            int rows = rs.getRow();
            rs.beforeFirst();
            return rows;
        } catch (SQLException e) {
            LOG.warn("Error getting result set size", e);
        }
        return 0;
    }

    /**
     * Closes the Cursor, releasing all of its resources and making it completely invalid.
     * Unlike deactivate() a call to requery() will not make the Cursor valid
     * again.
     */
    public void close() {
        try {
            rs.close();
        } catch (SQLException e) {
            LOG.warn("Error closing cursor result set", e);
        }
        try {
            statement.close();
        } catch (SQLException e) {
            LOG.warn("Error closing cursor inner statement", e);
        }
    }

    /**
     * Move the cursor to an absolute position. The valid
     * range of values is -1 &lt;= position &lt;= count.
     *
     * <p>This method will return true if the request destination was reachable,
     * otherwise, it returns false.
     *
     * @param offset the zero-based position to move to.
     * @return whether the requested move fully succeeded.
     */
    @SuppressWarnings("unused")
    public boolean moveToPosition(int offset) {
        try {
            return rs.relative(offset);
        } catch (SQLException e) {
            LOG.warn("Error moving inside the result set, offset: " + offset, e);
        }
        return false;
    }

    /**
     * Move the cursor to the next row.
     *
     * <p>This method will return false if the cursor is already past the
     * last entry in the result set.
     *
     * @return whether the move succeeded.
     */
    public boolean moveToNext() {
        try {
            return rs.next();
        } catch (SQLException e) {
            LOG.warn("Error moving inside the result set, to next", e);
        }
        return false;
    }

    @SuppressWarnings("unused")
    public byte getByte(int columnIndex) {
        try {
            return rs.getByte(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return 0;
    }

    @SuppressWarnings("unused")
    public boolean getBoolean(int columnIndex) {
        try {
            return rs.getBoolean(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return false;
    }

    public byte[] getBytes(int columnIndex) {
        try {
            return rs.getBytes(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return null;
    }
}
