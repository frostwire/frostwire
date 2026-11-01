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
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import com.andrew.apollo.provider.RecentStore;
import com.frostwire.util.Logger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Used to query {@link RecentStore} and return the last listened to albums.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (gubatron@gmail.com)
 */
public class RecentLoader extends SongLoader {

  private static final Logger LOG = Logger.getLogger(RecentLoader.class);

  /**
   * Constructor of <code>RecentLoader</code>
   *
   * @param context The {@link Context} to use
   */
  public RecentLoader(final Context context) {
    super(context);
  }

  @Override
  public Cursor makeCursor(Context context) {
    return makeRecentCursor(getContext());
  }

  /**
   * Creates the {@link Cursor} used to run the query.
   *
   * @param context The {@link Context} to use.
   * @return The {@link Cursor} used to run the album query.
   */
  private static Cursor makeRecentCursor(final Context context) {
    if (context == null) {
      return null;
    }

    RecentStore recentStore = RecentStore.getInstance(context);

    if (recentStore == null) {
      return null;
    }

    removeOrphanedEntries(context, recentStore);

    String durationColumn = "duration";
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      durationColumn = AudioColumns.DURATION;
    }
    return recentStore
        .getReadableDatabase()
        .query(
            RecentStore.TABLE_NAME,
            new String[] {
              BaseColumns._ID + " as id", /* 0 - id */
              AudioColumns.TITLE, /* 2 - songname */
              AudioColumns.ARTIST, /* 3 - artistname */
              AudioColumns.ALBUM, /* 4 - albumname */
              durationColumn, /* 5 - duration */
              /* Can't add AudioColumns.ALBUM_ID since RecentStore is a sqlitedb created by Apollo and doesn't seem to need it */
            },
            null,
            null,
            null,
            null,
            RecentStore.RecentStoreColumns.LAST_TIME_PLAYED + " DESC");
  }

  private static void removeOrphanedEntries(Context context, RecentStore recentStore) {
    Cursor cursor = null;
    try {
      cursor =
          recentStore
              .getReadableDatabase()
              .query(
                  RecentStore.TABLE_NAME,
                  new String[] {BaseColumns._ID},
                  null,
                  null,
                  null,
                  null,
                  null);
      if (cursor == null) {
        return;
      }
      List<Long> recentIds = new ArrayList<>();
      while (cursor.moveToNext()) {
        recentIds.add(cursor.getLong(0));
      }
      cursor.close();
      cursor = null;

      if (recentIds.isEmpty()) {
        return;
      }

      StringBuilder selection = new StringBuilder(BaseColumns._ID + " IN (");
      for (int i = 0; i < recentIds.size(); i++) {
        if (i > 0) {
          selection.append(",");
        }
        selection.append(recentIds.get(i));
      }
      selection.append(")");

      Set<Long> validIds = null;
      Cursor mediaCursor = null;
      try {
        mediaCursor =
            context
                .getContentResolver()
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] {BaseColumns._ID},
                    selection.toString(),
                    null,
                    null);
        if (mediaCursor != null) {
          validIds = new HashSet<>();
          while (mediaCursor.moveToNext()) {
            validIds.add(mediaCursor.getLong(0));
          }
        }
      } catch (Throwable t) {
        LOG.error("removeOrphanedEntries: MediaStore query failed", t);
      } finally {
        if (mediaCursor != null) {
          mediaCursor.close();
        }
      }

      List<Long> orphans = orphans(recentIds, validIds);
      if (!orphans.isEmpty()) {
        recentStore.removeItems(orphans);
        LOG.info(
            "removeOrphanedEntries: removed "
                + orphans.size()
                + " orphaned entries from RecentStore");
      }
    } catch (Throwable t) {
      LOG.error("removeOrphanedEntries: failed", t);
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }

  /**
   * The Recent entries to remove: those MediaStore no longer has. {@code inMediaStore} is null when
   * the MediaStore query failed or returned no cursor (provider not ready, permission revoked);
   * then nothing is removed. A song MediaStore has but hasn't read a duration for yet (a fresh
   * download) stays. Both used to wipe entries that were fine (GitHub #1443).
   */
  static List<Long> orphans(List<Long> recentIds, Set<Long> inMediaStore) {
    List<Long> orphans = new ArrayList<>();
    if (inMediaStore == null) {
      return orphans;
    }
    for (Long id : recentIds) {
      if (!inMediaStore.contains(id)) {
        orphans.add(id);
      }
    }
    return orphans;
  }
}
