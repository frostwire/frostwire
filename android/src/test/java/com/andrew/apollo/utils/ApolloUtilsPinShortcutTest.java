/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.os.Looper;
import android.provider.MediaStore;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.Config;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.ui.activities.ShortcutActivity;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowShortcutManager;

/** "Pin to home screen" from the Apollo lists: builds the shortcut off the main thread. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class ApolloUtilsPinShortcutTest {

  private Activity activity;
  private ShadowShortcutManager shortcuts;
  private ShortcutManager manager;
  private ImageFetcher fetcher;

  @Before
  public void setUp() throws Exception {
    ApolloServiceFixture.installPlatform(true);
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    FakeConfig.install();
    fetcher = mock(ImageFetcher.class);
    setFetcher(fetcher);
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    manager = activity.getSystemService(ShortcutManager.class);
    shortcuts = shadowOf(manager);
    shortcuts.setIsRequestPinShortcutSupported(true);
  }

  @After
  public void tearDown() throws Exception {
    setFetcher(null);
    FakeConfig.uninstall();
  }

  private static void setFetcher(ImageFetcher value) throws Exception {
    Field field = ImageFetcher.class.getDeclaredField("sInstance");
    field.setAccessible(true);
    field.set(null, value);
  }

  /**
   * The task ends by posting its toast to the main looper, which is not run here (the toast keeps a
   * looper busy): a task waiting there is a finished task.
   */
  private static void waitForTheTaskToFinish() {
    BooleanSupplier finished = () -> !shadowOf(Looper.getMainLooper()).isIdle();
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (!finished.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertTrue("the shortcut task finished", finished.getAsBoolean());
  }

  @Test
  public void anAlbumIsPinnedWithItsNameIconAndAShortcutActivityIntent() {
    Bitmap art = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
    when(fetcher.getDefaultArtwork()).thenReturn(art);

    ApolloUtils.createShortcutIntentAsync(
        "Greatest Hits",
        "Ann",
        5L,
        MediaStore.Audio.Albums.CONTENT_TYPE,
        new WeakReference<>(activity));
    waitForTheTaskToFinish();

    assertEquals(1, manager.getPinnedShortcuts().size());
    ShortcutInfo pinned = manager.getPinnedShortcuts().get(0);
    assertEquals(MediaStore.Audio.Albums.CONTENT_TYPE + "_5", pinned.getId());
    assertEquals("Greatest Hits", pinned.getShortLabel().toString());
    Intent intent = pinned.getIntent();
    assertEquals(ShortcutActivity.class.getName(), intent.getComponent().getClassName());
    assertEquals(5L, intent.getLongExtra(Config.ID, -1));
    assertEquals("Greatest Hits", intent.getStringExtra(Config.NAME));
    assertEquals(MediaStore.Audio.Albums.CONTENT_TYPE, intent.getStringExtra(Config.MIME_TYPE));
    assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  @Test
  public void aLauncherThatCantPinShortcutsGetsNoRequest() {
    shortcuts.setIsRequestPinShortcutSupported(false);
    when(fetcher.getDefaultArtwork())
        .thenReturn(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888));

    ApolloUtils.createShortcutIntentAsync(
        "Greatest Hits",
        "Ann",
        5L,
        MediaStore.Audio.Albums.CONTENT_TYPE,
        new WeakReference<>(activity));
    waitForTheTaskToFinish();

    assertTrue(manager.getPinnedShortcuts().isEmpty());
  }

  @Test
  public void withoutAnyArtworkThePinFailsAndNoShortcutIsRequested() {
    when(fetcher.getDefaultArtwork()).thenReturn(null);

    ApolloUtils.createShortcutIntentAsync(
        "Greatest Hits",
        "Ann",
        5L,
        MediaStore.Audio.Albums.CONTENT_TYPE,
        new WeakReference<>(activity));
    waitForTheTaskToFinish();

    assertTrue(manager.getPinnedShortcuts().isEmpty());
  }
}
