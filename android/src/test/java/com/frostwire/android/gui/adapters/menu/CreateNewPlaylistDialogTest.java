/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The music player had two "new playlist" dialogs (GitHub #1449): one overwrote a playlist with the
 * same name, emptying it without asking; the other suggested "Name+". The one kept never
 * overwrites: a taken name gets "Name 2" suggested, and the user confirms it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class CreateNewPlaylistDialogTest {

  @Test
  public void aFreeNameIsKept() {
    assertEquals("Rock", CreateNewPlaylistMenuAction.uniquePlaylistName("Rock", n -> false));
  }

  @Test
  public void aTakenNameGetsTheFirstFreeNumber() {
    Set<String> taken = Set.of("Rock", "Rock 2", "Rock 3");
    assertEquals("Rock 4", CreateNewPlaylistMenuAction.uniquePlaylistName("Rock", taken::contains));
  }

  @Test
  public void aTakenNumberedNameCountsOnFromItsNumber() {
    Set<String> taken = Set.of("Rock 2");
    assertEquals(
        "Rock 3", CreateNewPlaylistMenuAction.uniquePlaylistName("Rock 2", taken::contains));
  }

  /** MediaStore with no playlists; records created playlists and where songs are added. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<String> created = new CopyOnWriteArrayList<>();
    static final List<String> addedTo = new CopyOnWriteArrayList<>();
    static boolean creatingFails;

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      return new MatrixCursor(projection);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      if (creatingFails || !uri.getPath().endsWith("/playlists")) {
        return null;
      }
      created.add(values.getAsString("name"));
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
    FakeMediaProvider.created.clear();
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

  private static long createAndFill(String name) throws InterruptedException {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicLong id = new AtomicLong();
    Thread worker =
        new Thread(
            () -> id.set(CreateNewPlaylistMenuAction.createAndFill(context, name, new long[] {5})));
    worker.start();
    worker.join(5_000);
    return id.get();
  }

  @Test
  public void theSongsGoIntoTheNewPlaylist() throws Exception {
    assertEquals(20L, createAndFill("Jazz"));

    assertEquals(List.of("Jazz"), FakeMediaProvider.created);
    assertEquals(List.of("20"), FakeMediaProvider.addedTo);
  }

  @Test
  public void aPlaylistThatCouldNotBeCreatedGetsNoSongs() throws Exception {
    FakeMediaProvider.creatingFails = true;

    assertEquals(-1L, createAndFill("Jazz"));

    assertEquals(List.of(), FakeMediaProvider.addedTo);
  }
}
