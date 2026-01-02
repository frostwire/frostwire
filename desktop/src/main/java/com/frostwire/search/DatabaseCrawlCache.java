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
            String[] projection = new String[]{"count(*) as total"};
            c = db.query(projection, null, null, null);
            if (c != null) {
                size = c.getInt(1);
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
