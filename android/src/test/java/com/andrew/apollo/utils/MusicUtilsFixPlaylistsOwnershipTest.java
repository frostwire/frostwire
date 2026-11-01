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
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * After the storage permission was granted, FrostWire "fixed" playlists it didn't own by copying
 * each one to "Name " and deleting the original (GitHub #1449). Below Android 11 nothing has an
 * owner, so every playlist, FrostWire's own included, was copied again on every grant, with a new
 * id and one more trailing space. On 11 and later other apps' playlists were copied too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class MusicUtilsFixPlaylistsOwnershipTest {

  /** Playlists: 1 "Mine" (FrostWire's), 2 "Theirs" (another app's), 3 "Orphan" (no owner). */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<String> created = new CopyOnWriteArrayList<>();
    static final List<Long> deleted = new CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      if (!uri.getPath().endsWith("/playlists")) {
        return cursor;
      }
      Object[][] playlists = {
        {1L, "Mine", getContext().getPackageName()},
        {2L, "Theirs", "com.example.other"},
        {3L, "Orphan", null}
      };
      for (Object[] playlist : playlists) {
        if (args != null && !List.of(args).contains((String) playlist[1])) {
          continue;
        }
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
          switch (projection[i]) {
            case "_id":
              row[i] = playlist[0];
              break;
            case "name":
              row[i] = playlist[1];
              break;
            default:
              row[i] = playlist[2];
          }
        }
        cursor.addRow(row);
      }
      return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      if (uri.getPath().endsWith("/playlists")) {
        created.add(values.getAsString("name"));
        return ContentUris.withAppendedId(uri, 100 + created.size());
      }
      return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      if (uri.getPath().endsWith("/playlists") && args != null) {
        deleted.add(Long.parseLong(args[0]));
      }
      return 1;
    }

    @Override
    public String getType(Uri uri) {
      return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      return 0;
    }
  }

  @Before
  public void setUp() {
    FakeMediaProvider.created.clear();
    FakeMediaProvider.deleted.clear();
    Robolectric.setupContentProvider(FakeMediaProvider.class, MediaStore.AUTHORITY);
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
  }

  private static void fixOnAWorker() throws InterruptedException {
    Thread worker =
        new Thread(
            () -> MusicUtils.fixPlaylistsOwnership(ApplicationProvider.getApplicationContext()));
    worker.start();
    worker.join(5_000);
  }

  @Test
  @Config(sdk = 29)
  public void playlistsHaveNoOwnerBeforeAndroid11SoNoneIsCopied() throws Exception {
    fixOnAWorker();

    assertEquals(List.of(), FakeMediaProvider.created);
    assertEquals(List.of(), FakeMediaProvider.deleted);
  }

  @Test
  @Config(sdk = 34)
  public void onlyPlaylistsWithoutAnOwnerAreTakenOver() throws Exception {
    fixOnAWorker();

    assertEquals(List.of("Orphan "), FakeMediaProvider.created);
    assertEquals(List.of(3L), FakeMediaProvider.deleted);
  }
}
