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
 * Looking a playlist up by name found another app's playlist with the same name as one of
 * FrostWire's, so "Add to playlist" and "Save as playlist" could change it (GitHub #1448).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsPlaylistIdTest {

  private static final String OTHER_APP = "com.example.other";

  /** Two playlists named "Road": another app's (999) listed first, then FrostWire's (100). */
  public static final class FakeMediaProvider extends ContentProvider {
    static final List<String> nameLookups = new CopyOnWriteArrayList<>();

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
      boolean byName = selection != null && selection.matches(".*\\bname\\s*=.*");
      if (byName) {
        nameLookups.add(selection);
      }
      String ownPackage = getContext().getPackageName();
      Object[][] playlists = {{999L, "Road", OTHER_APP}, {100L, "Road", ownPackage}};
      int arg = 0;
      for (Object[] playlist : playlists) {
        boolean matches = true;
        if (selection != null) {
          arg = 0;
          if (selection.contains("owner_package_name")) {
            matches &= playlist[2].equals(args[arg++]);
          }
          if (byName) {
            matches &= playlist[1].equals(args[arg]);
          }
        }
        if (matches) {
          Object[] row = new Object[projection.length];
          for (int i = 0; i < projection.length; i++) {
            row[i] = "_id".equals(projection[i]) ? playlist[0] : playlist[1];
          }
          cursor.addRow(row);
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
    FakeMediaProvider.nameLookups.clear();
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
  public void aPlaylistNameFindsFrostWiresOwnPlaylist() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicLong id = new AtomicLong();
    Thread worker = new Thread(() -> id.set(MusicUtils.getIdForPlaylist(context, "Road")));
    worker.start();
    worker.join(5_000);

    assertEquals(100L, id.get());
  }
}
