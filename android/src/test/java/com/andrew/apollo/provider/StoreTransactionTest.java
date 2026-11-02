/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.provider;

import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Adding a song to Favorites or Recent wrote inside a transaction it only ended when every write
 * succeeded (GitHub #1455). A failed write left the store's shared connection in an open
 * transaction, so later writes were never committed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class StoreTransactionTest {

  private static SQLiteDatabase database(Object store, String field) throws Exception {
    Field f = store.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return (SQLiteDatabase) f.get(store);
  }

  /** Makes the delete inside addSongId's transaction fail for a song already stored. */
  private static void failDeletesFrom(SQLiteDatabase db, String table) {
    db.execSQL(
        "CREATE TRIGGER fail_delete BEFORE DELETE ON "
            + table
            + " BEGIN SELECT RAISE(ABORT, 'write failed'); END");
  }

  @Test
  public void aFailedFavoriteWriteEndsItsTransaction() throws Exception {
    FavoritesStore store = FavoritesStore.getInstance(ApplicationProvider.getApplicationContext());
    SQLiteDatabase db = database(store, "writeableDatabase");
    store.addSongId(1L, "song", "album", "artist");
    failDeletesFrom(db, "favorites");
    try {
      try {
        store.addSongId(1L, "song", "album", "artist");
      } catch (RuntimeException expected) {
        // the delete failed
      }
      assertFalse("the transaction was left open", db.inTransaction());
    } finally {
      db.execSQL("DROP TRIGGER fail_delete");
    }
  }

  @Test
  public void aFailedRecentWriteEndsItsTransaction() throws Exception {
    RecentStore store = RecentStore.getInstance(ApplicationProvider.getApplicationContext());
    SQLiteDatabase db = database(store, "writableDatabase");
    store.addSongId(1L, "song", "artist", "album", 1000L, "unknown");
    failDeletesFrom(db, RecentStore.TABLE_NAME);
    try {
      store.addSongId(1L, "song", "artist", "album", 1000L, "unknown");
      assertFalse("the transaction was left open", db.inTransaction());
    } finally {
      db.execSQL("DROP TRIGGER fail_delete");
    }
  }
}
