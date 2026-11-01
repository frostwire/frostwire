/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;

/**
 * The songs waiting for Android's "Allow FrostWire to delete?" answer were kept only in a static
 * field. When Android killed the app while its dialog was up, the answer came back to a new process
 * that no longer knew which songs to clean out of playlists, Recent and Favorites (GitHub #1450).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsPendingDeleteRestoreTest {

  private static final long[] SONGS = {21L, 22L};

  /** MediaStore with the two songs and one playlist; records the cleanup's playlist deletes. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<String> memberDeletes = new CopyOnWriteArrayList<>();

    @Override
    public boolean onCreate() {
      return true;
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      MatrixCursor cursor = new MatrixCursor(projection);
      if (uri.getPath().contains("/playlists")) {
        cursor.addRow(new Object[] {300L, "playlist"});
      } else if (selection != null && selection.startsWith(MediaStore.Audio.Media._ID + " IN")) {
        for (long id : SONGS) {
          cursor.addRow(new Object[] {id});
        }
      }
      return cursor;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
      Bundle result = new Bundle();
      result.putParcelable(
          "result",
          PendingIntent.getActivity(
              getContext(), 0, new Intent("delete-request"), PendingIntent.FLAG_IMMUTABLE));
      return result;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
      if (uri.getPath().endsWith("/members")) {
        memberDeletes.add(selection);
      }
      return 0;
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
    FakeMediaProvider.memberDeletes.clear();
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

  @Test
  public void theAnswerStillCleansUpAfterTheAppWasKilled() throws Exception {
    ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
    Activity activity = controller.get();
    MusicUtils.deleteTracks(activity, SONGS, false, null, null);
    ShadowActivity shadow = shadowOf(activity);
    long deadline = System.currentTimeMillis() + 5_000;
    while (shadow.getLastIntentSenderRequest() == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
      ShadowLooper.idleMainLooper();
    }
    ShadowActivity.IntentSenderRequest request = shadow.getLastIntentSenderRequest();
    assertNotNull("Android's delete request is launched", request);

    Bundle saved = new Bundle();
    MusicUtils.savePendingTrackDelete(saved);
    forgetPendingTrackDelete(); // what a new process starts with
    MusicUtils.restorePendingTrackDelete(activity, saved);

    assertTrue(
        MusicUtils.handleDeleteTracksActivityResult(
            activity, request.requestCode, Activity.RESULT_OK));
    deadline = System.currentTimeMillis() + 5_000;
    while (FakeMediaProvider.memberDeletes.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    assertEquals(List.of("audio_id IN (21,22)"), FakeMediaProvider.memberDeletes);
  }

  @Test
  public void nothingIsRestoredWhenNoDeleteWasWaiting() throws Exception {
    forgetPendingTrackDelete();
    Bundle saved = new Bundle();
    MusicUtils.savePendingTrackDelete(saved);
    assertTrue(saved.isEmpty());

    MusicUtils.restorePendingTrackDelete(
        Robolectric.buildActivity(Activity.class).setup().get(), saved);
    Field field = MusicUtils.class.getDeclaredField("pendingTrackDelete");
    field.setAccessible(true);
    assertEquals(null, field.get(null));
  }

  private static void forgetPendingTrackDelete() throws Exception {
    Field field = MusicUtils.class.getDeclaredField("pendingTrackDelete");
    field.setAccessible(true);
    field.set(null, null);
  }
}
