/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
