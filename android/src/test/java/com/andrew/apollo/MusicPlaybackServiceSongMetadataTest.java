/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.media3.common.MediaMetadata;
import androidx.test.core.app.ApplicationProvider;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Controllers that show the whole queue (Android Auto, Wear, car screens) got a title for the song
 * playing only (GitHub #1452). The queue's songs now carry the metadata MediaStore has for them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceSongMetadataTest {

  /** Songs 1 and 2; 2 has no album. */
  public static final class FakeMediaProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      cursor.addRow(new Object[] {1L, "First", "Artist A", "Album A", 77L});
      cursor.addRow(new Object[] {2L, "Second", "Artist B", null, 0L});
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
    Robolectric.setupContentProvider(FakeMediaProvider.class, MediaStore.AUTHORITY);
  }

  @Test
  public void everySongGetsItsTitleArtistAlbumAndArt() {
    Map<Long, MediaMetadata> found =
        MusicPlaybackService.songMetadata(
            ApplicationProvider.getApplicationContext(), new long[] {1, 2});

    assertEquals("First", String.valueOf(found.get(1L).title));
    assertEquals("Artist A", String.valueOf(found.get(1L).artist));
    assertEquals("Album A", String.valueOf(found.get(1L).albumTitle));
    assertNotNull(found.get(1L).artworkUri);
    assertEquals("Second", String.valueOf(found.get(2L).title));
    assertEquals(null, found.get(2L).artworkUri);
    assertFalse(found.containsKey(3L));
  }
}
