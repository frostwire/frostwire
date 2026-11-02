/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.menu;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
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
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * "Save as playlist" (GitHub #1449): it looked the typed name up as typed but created the playlist
 * capitalized, so saving "rock" twice made two "Rock" playlists; and when creating failed it still
 * added the songs, to playlist -1.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class CreateNewPlaylistSaveTest {

  /** FrostWire's playlist 10, "Rock". */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<String> createdPlaylists = new CopyOnWriteArrayList<>();
    static final List<String> addedTo = new CopyOnWriteArrayList<>();
    static boolean creatingFails;

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      if (uri.getPath().endsWith("/playlists") && args != null && List.of(args).contains("Rock")) {
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
          row[i] = "_id".equals(projection[i]) ? (Object) 10L : "Rock";
        }
        cursor.addRow(row);
      }
      return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      if (creatingFails || !uri.getPath().endsWith("/playlists")) {
        return null;
      }
      createdPlaylists.add(values.getAsString("name"));
      return ContentUris.withAppendedId(uri, 20);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
      addedTo.add(uri.getPathSegments().get(uri.getPathSegments().size() - 2));
      return values.length;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      return 0;
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
    FakeMediaProvider.createdPlaylists.clear();
    FakeMediaProvider.addedTo.clear();
    FakeMediaProvider.creatingFails = false;
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

  private static long save(String typedName) throws InterruptedException {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicLong id = new AtomicLong();
    Thread worker =
        new Thread(
            () ->
                id.set(CreateNewPlaylist.saveSongsAsPlaylist(context, typedName, new long[] {5})));
    worker.start();
    worker.join(5_000);
    return id.get();
  }

  @Test
  public void aNameThatDiffersOnlyInCaseSavesIntoTheSamePlaylist() throws Exception {
    assertEquals(10L, save("rock"));

    assertEquals(List.of(), FakeMediaProvider.createdPlaylists);
    assertEquals(List.of("10"), FakeMediaProvider.addedTo);
  }

  @Test
  public void aPlaylistThatCouldNotBeCreatedGetsNoSongs() throws Exception {
    FakeMediaProvider.creatingFails = true;

    assertEquals(-1L, save("jazz"));

    assertEquals(List.of(), FakeMediaProvider.addedTo);
  }
}
