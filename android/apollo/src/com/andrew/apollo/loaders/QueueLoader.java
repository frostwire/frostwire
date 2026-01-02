/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.Cursor;

/**
 * Used to return the current playlist or queue.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class QueueLoader extends SongLoader {
    /**
     * Constructor of <code>QueueLoader</code>
     * 
     * @param context The {@link Context} to use
     */
    public QueueLoader(final Context context) {
        super(context);
    }

    /**
     * Creates the {@link Cursor} used to run the query.
     * 
     * @param context The {@link Context} to use.
     * @return The {@link Cursor} used to run the song query.
     */
    public Cursor makeCursor(final Context context) {
        return makeQueueCursor(context);
    }

    public static Cursor makeQueueCursor(final Context context) { return new NowPlayingCursor(context); }
}
