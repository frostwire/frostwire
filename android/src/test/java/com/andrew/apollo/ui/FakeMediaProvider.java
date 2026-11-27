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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.Robolectric;

/**
 * A stand-in for the MediaStore: every query answers with the rows the test set, with exactly the
 * columns that were asked for (a column a row does not define reads as null or 0), so the screens
 * that load their lists through loaders can be shown real rows.
 */
public final class FakeMediaProvider extends ContentProvider {

  private static final List<Map<String, Object>> ROWS = new ArrayList<>();

  /** Selection and arguments of the latest query, for assertions on what was asked. */
  public static volatile String lastSelection;

  public static volatile String[] lastSelectionArgs;

  /** Every query the provider has answered, as {@code uri}. */
  public static final List<String> QUERIES = new java.util.concurrent.CopyOnWriteArrayList<>();

  /** How many deletes the provider has been asked for. */
  public static final java.util.concurrent.atomic.AtomicInteger DELETES =
      new java.util.concurrent.atomic.AtomicInteger();

  /** What a delete answers: the number of rows the MediaStore says it removed. */
  public static volatile int deleteResult = 0;

  /** Registers the provider for the MediaStore authority and starts it with no rows. */
  public static void install() {
    deleteResult = 0;
    DELETES.set(0);
    clear();
    lastSelection = null;
    lastSelectionArgs = null;
    QUERIES.clear();
    Robolectric.setupContentProvider(FakeMediaProvider.class, "media");
  }

  /** A row with the given column name/value pairs. */
  public static void addRow(Object... namesAndValues) {
    Map<String, Object> row = new HashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      row.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    synchronized (ROWS) {
      ROWS.add(row);
    }
  }

  /** A row for an artist, as the search screen's cursor lists them. */
  public static void addArtist(long id, String artist) {
    addRow("_id", id, "mime_type", "artist", "artist", artist, "artist_id", id);
  }

  public static void addAlbum(long id, String album, String artist) {
    addRow("_id", id, "mime_type", "album", "artist", artist, "album", album, "album_id", id);
  }

  public static void addSong(long id, String title, String album, String artist) {
    addRow(
        "_id",
        id,
        "mime_type",
        "audio/mpeg",
        "artist",
        artist,
        "album",
        album,
        "title",
        title,
        "_data",
        "/music/" + title,
        "duration",
        180000L,
        "album_id",
        1L,
        "artist_id",
        1L,
        "audio_id",
        id);
  }

  /**
   * One row that answers every list the music browser loads (an artist, an album, a song and a
   * playlist at once): the loaders each read the columns they asked for.
   */
  public static void addLibraryRow() {
    addRow(
        "_id",
        1L,
        "artist",
        "The Beatles",
        "album",
        "Help!",
        "title",
        "Help",
        "name",
        "Mix",
        "number_of_albums",
        1,
        "number_of_tracks",
        2,
        "numsongs",
        2,
        "minyear",
        "1965",
        "duration",
        180000L,
        "album_id",
        5L,
        "artist_id",
        1L,
        "audio_id",
        1L);
  }

  public static void clear() {
    synchronized (ROWS) {
      ROWS.clear();
    }
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
    QUERIES.add(String.valueOf(uri));
    String[] columns =
        projection != null ? projection : new String[] {"_id", "title", "artist", "album"};
    MatrixCursor cursor = new MatrixCursor(columns);
    synchronized (ROWS) {
      for (Map<String, Object> row : ROWS) {
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
          values[i] = row.get(columns[i]);
        }
        cursor.addRow(values);
      }
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
    DELETES.incrementAndGet();
    return deleteResult;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    return 0;
  }
}
