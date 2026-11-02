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
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Home-screen shortcuts showed the default icon: their image was looked up in a cache nothing ever
 * wrote (GitHub #1461). They now show the album art of the album, or of a playlist's first song on
 * an album.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@SuppressWarnings("deprecation")
public class ApolloUtilsShortcutAlbumTest {

  /** Playlist 3 holds songs 11 (no album) and 12 (album 77). */
  public static final class FakeMediaProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      if (uri.getPath().endsWith("/playlists/3/members")) {
        cursor.addRow(new Object[] {11L});
        cursor.addRow(new Object[] {12L});
      } else if (selection != null && selection.startsWith("_id=12")) {
        cursor.addRow(new Object[] {77L});
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
    Robolectric.setupContentProvider(FakeMediaProvider.class, MediaStore.AUTHORITY);
  }

  @Test
  public void anAlbumShortcutShowsThatAlbum() {
    Context context = ApplicationProvider.getApplicationContext();
    assertEquals(
        5L,
        ApolloUtils.shortcutAlbumId(
            context, MediaStore.Audio.Albums.CONTENT_TYPE, 5, "Album", "Artist"));
  }

  @Test
  public void aPlaylistShortcutShowsItsFirstSongOnAnAlbum() {
    Context context = ApplicationProvider.getApplicationContext();
    assertEquals(
        77L,
        ApolloUtils.shortcutAlbumId(
            context, MediaStore.Audio.Playlists.CONTENT_TYPE, 3, "Road trip", null));
  }
}
