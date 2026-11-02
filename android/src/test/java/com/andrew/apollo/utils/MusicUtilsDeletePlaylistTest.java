/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Deleting a playlist ran MediaStore's delete on the main thread, from the confirmation dialog
 * (GitHub #1449).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsDeletePlaylistTest {

  /** Deletes any playlist except 404, which (like another app's on Android 11+) deletes nothing. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<Thread> deleteThreads = new CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      deleteThreads.add(Thread.currentThread());
      return "404".equals(args[0]) ? 0 : 1;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      return null;
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
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
      return 0;
    }
  }

  @Before
  public void setUp() {
    FakeMediaProvider.deleteThreads.clear();
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

  private static int deleteAndWait(long playlistId) throws InterruptedException {
    AtomicInteger callbacks = new AtomicInteger();
    MusicUtils.deletePlaylistInBackground(
        ApplicationProvider.getApplicationContext(), playlistId, callbacks::incrementAndGet);
    long deadline = System.currentTimeMillis() + 5_000;
    while (FakeMediaProvider.deleteThreads.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    Thread.sleep(50);
    ShadowLooper.idleMainLooper();
    return callbacks.get();
  }

  @Test
  public void thePlaylistIsDeletedOffTheMainThreadThenTheListRefreshes() throws Exception {
    assertEquals("the list refreshes once", 1, deleteAndWait(7));

    assertEquals(1, FakeMediaProvider.deleteThreads.size());
    assertNotSame(Thread.currentThread(), FakeMediaProvider.deleteThreads.get(0));
  }

  @Test
  public void aPlaylistThatWasNotDeletedDoesNotRefreshTheList() throws Exception {
    assertEquals(0, deleteAndWait(404));
  }

  /** Another app's playlist (Android 11+) deletes nothing: the user is told why. */
  @Test
  public void aPlaylistThatWasNotDeletedRunsTheNotDeletedCallback() throws Exception {
    AtomicInteger deleted = new AtomicInteger();
    AtomicInteger notDeleted = new AtomicInteger();
    MusicUtils.deletePlaylistInBackground(
        ApplicationProvider.getApplicationContext(),
        404,
        deleted::incrementAndGet,
        notDeleted::incrementAndGet);
    long deadline = System.currentTimeMillis() + 5_000;
    while (notDeleted.get() == 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
      ShadowLooper.idleMainLooper();
    }

    assertEquals(1, notDeleted.get());
    assertEquals(0, deleted.get());
  }
}
