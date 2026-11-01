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
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * "Add to playlist" (GitHub #1448): on Android 8 and 9 it asked MediaStore for the
 * "external_primary" volume, which those versions don't have, and added nothing; and every add
 * numbered its songs from PLAY_ORDER 1000, so songs from different adds ended up in no particular
 * order.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class MusicUtilsAddToPlaylistTest {

  private static final long PLAYLIST = 7L;

  /** One playlist's members; only answers on the volumes the running Android version has. */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<long[]> members = new ArrayList<>(); // {audio id, play order}
    static final List<String> memberUris = new ArrayList<>();
    static boolean onlyLegacyVolumes;

    @Override
    public boolean onCreate() {
      return true;
    }

    private static boolean known(Uri uri) {
      memberUris.add(uri.getPath());
      return !onlyLegacyVolumes || uri.getPathSegments().get(0).equals("external");
    }

    @Override
    public Cursor query(
        Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
      if (!uri.getPath().endsWith("/members") || !known(uri)) {
        return null;
      }
      MatrixCursor cursor = new MatrixCursor(projection);
      for (long[] member : members) {
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
          switch (projection[i]) {
            case "audio_id":
              row[i] = member[0];
              break;
            case "play_order":
              row[i] = member[1];
              break;
            default:
              row[i] = members.indexOf(member) + 1L;
          }
        }
        cursor.addRow(row);
      }
      return cursor;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
      if (!known(uri)) {
        return 0;
      }
      for (ContentValues value : values) {
        members.add(new long[] {value.getAsLong("audio_id"), value.getAsLong("play_order")});
      }
      return values.length;
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
    FakeMediaProvider.members.clear();
    FakeMediaProvider.memberUris.clear();
    FakeMediaProvider.onlyLegacyVolumes = false;
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

  private static void add(long... ids) {
    MusicUtils.addToPlaylistSilently(ApplicationProvider.getApplicationContext(), ids, PLAYLIST);
  }

  @Test
  @Config(sdk = 28)
  public void addingWorksOnAndroidVersionsWithoutTheExternalPrimaryVolume() {
    FakeMediaProvider.onlyLegacyVolumes = true;

    add(1, 2);

    assertEquals(
        "songs added: " + FakeMediaProvider.memberUris, 2, FakeMediaProvider.members.size());
  }
}
