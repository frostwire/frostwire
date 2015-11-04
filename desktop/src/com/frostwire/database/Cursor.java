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

package com.frostwire.database;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.frostwire.logging.Logger;

/**
 * @author gubatron
 * @author aldenml
 *
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
     * If you expect the column to exist use {@link #getColumnIndexOrThrow(String)} instead, which
     * will make the error more clear.
     *
     * @param columnName the name of the target column.
     * @return the zero-based column index for the given column name, or -1 if
     * the column name does not exist.
     * @see #getColumnIndexOrThrow(String)
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
     * Unlike {@link #deactivate()} a call to {@link #requery()} will not make the Cursor valid
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
     * @param position the zero-based position to move to.
     * @return whether the requested move fully succeeded.
     */
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

    public byte getByte(int columnIndex) {
        try {
            return rs.getByte(columnIndex);
        } catch (SQLException e) {
            LOG.warn("Error reading typed result set value", e);
        }
        return 0;
    }

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
