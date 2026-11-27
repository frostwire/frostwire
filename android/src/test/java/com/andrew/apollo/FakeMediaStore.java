/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.robolectric.Robolectric;

/**
 * A MediaStore the tests script: queries are answered by {@link #onQuery}, and every write is
 * recorded so a test can say what the code under test did to the store. State is static because
 * Robolectric builds the provider itself; call {@link #install} in {@code @Before}.
 */
public final class FakeMediaStore extends ContentProvider {

  /** Answers a query; return null for "no cursor", as MediaStore does on an error. */
  public interface QueryHandler {
    Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder);
  }

  public static volatile QueryHandler onQuery = (u, p, s, a, o) -> null;
  public static volatile int insertedId = 100;
  public static volatile int deleteResult = 1;
  public static volatile int bulkInsertResult = -1;
  public static volatile RuntimeException failure;
  public static final List<String> queries = new ArrayList<>();
  public static final List<String> inserts = new ArrayList<>();
  public static final List<ContentValues> insertedValues = new ArrayList<>();
  public static final List<String> deletes = new ArrayList<>();
  public static final List<String> updates = new ArrayList<>();
  public static final List<ContentValues> updatedValues = new ArrayList<>();
  public static final List<String> bulkInserts = new ArrayList<>();
  public static final List<String> calls = new ArrayList<>();

  public static void install() {
    onQuery = (u, p, s, a, o) -> null;
    insertedId = 100;
    deleteResult = 1;
    bulkInsertResult = -1;
    failure = null;
    queries.clear();
    inserts.clear();
    insertedValues.clear();
    deletes.clear();
    updates.clear();
    updatedValues.clear();
    bulkInserts.clear();
    calls.clear();
    Robolectric.setupContentProvider(FakeMediaStore.class, MediaStore.AUTHORITY);
  }

  /** A row, as alternating column names and values. */
  public static Map<String, Object> row(Object... namesAndValues) {
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      row.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return row;
  }

  /** A cursor with the projection's columns (or the rows' own when null) filled from the rows. */
  @SafeVarargs
  public static Cursor cursor(String[] projection, Map<String, Object>... rows) {
    List<Map<String, Object>> list = new ArrayList<>();
    for (Map<String, Object> r : rows) {
      list.add(r);
    }
    return cursor(projection, list);
  }

  public static Cursor cursor(String[] projection, List<Map<String, Object>> rows) {
    String[] columns = projection;
    if (columns == null) {
      LinkedHashSet<String> names = new LinkedHashSet<>();
      for (Map<String, Object> r : rows) {
        names.addAll(r.keySet());
      }
      columns = names.toArray(new String[0]);
    }
    MatrixCursor cursor = new MatrixCursor(columns);
    for (Map<String, Object> r : rows) {
      Object[] values = new Object[columns.length];
      for (int i = 0; i < columns.length; i++) {
        values[i] = r.get(columns[i]);
      }
      cursor.addRow(values);
    }
    return cursor;
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Cursor query(
      Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
    queries.add(uri + "|" + selection);
    throwIfFailing();
    return onQuery.query(uri, projection, selection, args, sortOrder);
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    inserts.add(uri.toString());
    insertedValues.add(values);
    throwIfFailing();
    return insertedId < 0 ? null : Uri.withAppendedPath(uri, String.valueOf(insertedId));
  }

  @Override
  public int bulkInsert(Uri uri, ContentValues[] values) {
    bulkInserts.add(uri + "|" + values.length);
    throwIfFailing();
    return bulkInsertResult >= 0 ? bulkInsertResult : values.length;
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    deletes.add(uri + "|" + selection);
    throwIfFailing();
    return deleteResult;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    updates.add(uri + "|" + selection);
    updatedValues.add(values);
    throwIfFailing();
    return 1;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    calls.add(method + "|" + arg);
    return Bundle.EMPTY;
  }

  private static void throwIfFailing() {
    if (failure != null) {
      throw failure;
    }
  }
}
