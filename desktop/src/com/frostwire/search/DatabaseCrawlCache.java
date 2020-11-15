/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.

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

package com.frostwire.search;

import com.frostwire.content.ContentValues;
import com.frostwire.database.Cursor;
import com.frostwire.search.CrawlCacheDB.Columns;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.SearchSettings;

/**
 * @author gubatron
 * @author aldenml
 */
public class DatabaseCrawlCache implements CrawlCache {
    private static final Logger LOG = Logger.getLogger(DatabaseCrawlCache.class);
    private final CrawlCacheDB db;

    public DatabaseCrawlCache() {
        db = CrawlCacheDB.instance();
    }

    @Override
    public byte[] get(String key) {
        byte[] data = null;
        Cursor c = null;
        try {
            String[] columns = new String[]{Columns.DATA};
            String where = Columns.KEY + " = ?";
            String[] whereArgs = new String[]{key};
            c = db.query(columns, where, whereArgs, null);
            if (c.moveToNext()) {
                data = c.getBytes(c.getColumnIndex(Columns.DATA));
            }
        } catch (Throwable e) {
            LOG.warn("General failure getting cache data with key: " + key, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return data;
    }

    @Override
    public void put(String key, byte[] data) {
        if (SearchSettings.SMART_SEARCH_ENABLED.getValue()) {
            try {
                ContentValues values = new ContentValues();
                values.put(Columns.KEY, key);
                values.put(Columns.DATA, data);
                db.insert(values);
            } catch (Throwable e) {
                LOG.warn("Error putting value to crawl cache: " + e.getMessage());
            }
        }
    }

    @Override
    public void remove(String key) {
        try {
            String where = Columns.KEY + " = ?";
            String[] whereArgs = new String[]{key};
            db.delete(where, whereArgs);
        } catch (Throwable e) {
            LOG.warn("Error deleting value from crawl cache: " + e.getMessage());
        }
    }

    @Override
    public synchronized void clear() {
        try {
            db.truncate();
        } catch (Throwable e) {
            LOG.warn("Error deleting crawl cache: " + e.getMessage(), e);
        }
    }

    @Override
    public long numEntries() {
        long size = 0;
        Cursor c = null;
        try {
            String[] columns = new String[]{Columns.ID};
            String where = "";
            String[] whereArgs = new String[]{};
            c = db.query(columns, where, whereArgs, null);
            if (c != null) {
                size = c.getCount();
            }
        } catch (Exception e) {
            LOG.warn("Failed to get num of shared files", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return size;
    }

    @Override
    public long sizeInBytes() {
        return db.sizeInBytes();
    }
}
