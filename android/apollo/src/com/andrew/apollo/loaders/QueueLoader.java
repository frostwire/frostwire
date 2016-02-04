/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
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
