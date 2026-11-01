/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.loaders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ContentResolver;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The music loaders never re-queried: a song scanned into MediaStore after the screen loaded (a
 * finished download) didn't show until the screen was recreated (GitHub #1445). They now reload
 * when MediaStore's audio changes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class WrappedAsyncTaskLoaderMediaObserverTest {

  /** Counts loads instead of running them. */
  private static final class CountingLoader extends WrappedAsyncTaskLoader<List<String>> {
    int loads;

    CountingLoader() {
      super(ApplicationProvider.getApplicationContext());
    }

    @Override
    protected void onForceLoad() {
      loads++;
    }

    @Override
    public List<String> loadInBackground() {
      return List.of();
    }
  }

  @Test
  public void aMediaStoreChangeReloadsAStartedLoader() {
    ContentResolver resolver = ApplicationProvider.getApplicationContext().getContentResolver();
    CountingLoader loader = new CountingLoader();

    loader.startLoading();
    assertEquals("first load", 1, loader.loads);
    loader.deliverResult(List.of("song"));

    resolver.notifyChange(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals("a new song in MediaStore reloads the list", 2, loader.loads);
  }

  @Test
  public void aChangeWhileStoppedReloadsWhenStartedAgain() {
    ContentResolver resolver = ApplicationProvider.getApplicationContext().getContentResolver();
    CountingLoader loader = new CountingLoader();
    loader.startLoading();
    loader.deliverResult(List.of("song"));
    loader.stopLoading();

    resolver.notifyChange(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals("not while stopped", 1, loader.loads);

    loader.startLoading();
    assertEquals("the cached list, then a reload", 2, loader.loads);
  }

  @Test
  public void resettingTheLoaderStopsObserving() {
    CountingLoader loader = new CountingLoader();
    loader.startLoading();
    assertTrue(
        shadowOf(ApplicationProvider.getApplicationContext().getContentResolver())
                .getContentObservers(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                .size()
            > 0);

    loader.reset();

    assertEquals(
        0,
        shadowOf(ApplicationProvider.getApplicationContext().getContentResolver())
            .getContentObservers(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            .size());
  }
}
