/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.Robolectric;

/**
 * A stand-in for the MediaStore: every query answers with the rows the test set, whatever columns
 * were asked for, so the screens that load their lists through a {@code CursorLoader} can be shown
 * real rows. Rows are plain {@code column -> value} arrays over {@link #COLUMNS}.
 */
public final class FakeMediaProvider extends ContentProvider {

  /** Every column the Apollo list screens read. */
  public static final String[] COLUMNS = {
    "_id", "mime_type", "artist", "album", "title", "_data", "duration", "album_id", "artist_id"
  };

  private static final List<Object[]> ROWS = new ArrayList<>();

  /** Selection and arguments of the latest query, for assertions on what was asked. */
  public static volatile String lastSelection;

  public static volatile String[] lastSelectionArgs;

  /** Registers the provider for the MediaStore authority and starts it with no rows. */
  public static void install() {
    ROWS.clear();
    lastSelection = null;
    lastSelectionArgs = null;
    Robolectric.setupContentProvider(FakeMediaProvider.class, "media");
  }

  /** A row for an artist, as the search screen's cursor lists them. */
  public static void addArtist(long id, String artist) {
    ROWS.add(new Object[] {id, "artist", artist, null, null, null, 0L, 0L, id});
  }

  public static void addAlbum(long id, String album, String artist) {
    ROWS.add(new Object[] {id, "album", artist, album, null, null, 0L, id, 0L});
  }

  public static void addSong(long id, String title, String album, String artist) {
    ROWS.add(
        new Object[] {id, "audio/mpeg", artist, album, title, "/music/" + title, 1000L, 1L, 1L});
  }

  /** A row of any other mime type. */
  public static void addRow(long id, String mimeType, String title) {
    ROWS.add(new Object[] {id, mimeType, null, null, null, null, 0L, 0L, 0L});
  }

  public static void clear() {
    ROWS.clear();
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    lastSelection = selection;
    lastSelectionArgs = selectionArgs;
    MatrixCursor cursor = new MatrixCursor(COLUMNS);
    for (Object[] row : ROWS) {
      cursor.addRow(row);
    }
    return cursor;
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs) {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }
}
