/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.model.Song;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * "Add to Favorites" on an album or artist queried MediaStore once per song (GitHub #1455). The
 * songs now come from one query.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsGetSongsTest {

  /** Songs 1, 2 and 3; answers queries for any subset. */
  public static final class FakeMediaProvider extends ContentProvider {
    static int queries;

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      queries++;
      MatrixCursor cursor = new MatrixCursor(projection);
      for (long id = 1; id <= 3; id++) {
        if (selection.contains("_id IN (") && selection.matches(".*[(,]" + id + "[,)].*")) {
          cursor.addRow(new Object[] {id, "song " + id, "artist", "album", 180_000});
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
    public int delete(Uri uri, String selection, String[] args) {
      return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      return 0;
    }
  }

  @Before
  public void setUp() {
    FakeMediaProvider.queries = 0;
    Robolectric.setupContentProvider(FakeMediaProvider.class, MediaStore.AUTHORITY);
  }

  @Test
  public void theSongsComeFromOneQueryInTheOrderAsked() {
    List<Song> songs =
        MusicUtils.getSongs(ApplicationProvider.getApplicationContext(), new long[] {3, 9, 1});

    assertEquals(1, FakeMediaProvider.queries);
    List<String> names = new ArrayList<>();
    for (Song song : songs) {
      names.add(song.mSongName);
    }
    assertEquals("9 doesn't exist", List.of("song 3", "song 1"), names);
  }
}
