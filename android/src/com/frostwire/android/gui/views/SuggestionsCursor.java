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

package com.frostwire.android.gui.views;

import android.database.AbstractCursor;

import org.json.JSONArray;

/**
 * @author gubatron
 * @author aldenml
 */
class SuggestionsCursor extends AbstractCursor {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SUGGESTION = "suggestion";

    public static final String[] COLUMNS = {COLUMN_ID, COLUMN_SUGGESTION};

    private final JSONArray suggestions;

    public SuggestionsCursor(JSONArray suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public String[] getColumnNames() {
        return COLUMNS;
    }

    @Override
    public int getCount() {
        return suggestions.length();
    }

    @Override
    public double getDouble(int column) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat(int column) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt(int column) {
        if (column == 0) {
            return mPos;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(int column) {
        if (column == 0) {
            return mPos;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort(int column) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getString(int column) {
        if (column == 1) {
            try {
                return suggestions.getJSONArray(mPos).getString(0);
            } catch (Throwable e) {
                // ignore
            }
        }
        return null;
    }

    @Override
    public boolean isNull(int column) {
        throw new UnsupportedOperationException();
    }
}
