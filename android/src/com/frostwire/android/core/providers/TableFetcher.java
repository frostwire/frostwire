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

import com.frostwire.android.core.FWFileDescriptor;

/**
 * @author gubatron
 * @author aldenml
 */
public interface TableFetcher {

    String[] getColumns();

    String getSortByExpression();

    Uri getExternalContentUri();

    Uri getInternalContentUri();

    // IDEA, go back to internal storage and have the fetchers have 2 content URIs, internal and external volumes
    //Uri getInternalContentUri();

    void prepare(Cursor cur);

    FWFileDescriptor fetch(Cursor cur);

    byte getFileType();

    String where();

    String[] whereArgs();
}
