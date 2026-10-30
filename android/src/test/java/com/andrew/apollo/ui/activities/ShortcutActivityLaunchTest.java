/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.Config;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/**
 * frostwire/frostwire#1404: ShortcutActivity, opened by the music player's home screen shortcuts
 * and by voice "play ... on FrostWire", crashed on every launch: an AppCompatActivity with a Holo
 * theme and no content view throws in onPostCreate. It also did nothing since 2020, so it never
 * finished.
 */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class ShortcutActivityLaunchTest {

  @Before
  public void setUp() {
    // MusicUtils checks that MediaStore queries run off the UI thread; MainApplication would set
    // the real platform (application = Application.class).
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
  public void aShortcutLaunchDoesNotCrashAndFinishes() throws Exception {
    Intent shortcut =
        new Intent(ApplicationProvider.getApplicationContext(), ShortcutActivity.class)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(Config.ID, 42L)
            .putExtra(Config.NAME, "An album")
            .putExtra(Config.MIME_TYPE, MediaStore.Audio.Albums.CONTENT_TYPE);

    // Threw IllegalStateException "You need to use a Theme.AppCompat theme" in onPostCreate.
    ActivityController<ShortcutActivity> controller =
        Robolectric.buildActivity(ShortcutActivity.class, shortcut).setup();
    ShortcutActivity activity = controller.get();

    // The album's songs are looked up in the background; this test library has none.
    long deadline = System.currentTimeMillis() + 10_000;
    while (!activity.isFinishing() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    assertTrue("the shortcut activity never finished", activity.isFinishing());
    assertNull(
        "nothing to play: the player must not open",
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity());
  }

  @Test
  public void aLaunchWithNothingToPlayFinishesAtOnce() {
    Intent empty = new Intent(ApplicationProvider.getApplicationContext(), ShortcutActivity.class);

    ShortcutActivity activity =
        Robolectric.buildActivity(ShortcutActivity.class, empty).setup().get();

    assertTrue(activity.isFinishing());
  }

  @Test
  public void aVoiceSearchWithNoMatchFinishesWithoutOpeningThePlayer() throws Exception {
    Intent voice =
        new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setClass(ApplicationProvider.getApplicationContext(), ShortcutActivity.class)
            .putExtra(android.app.SearchManager.QUERY, "nothing by this name");

    ShortcutActivity activity =
        Robolectric.buildActivity(ShortcutActivity.class, voice).setup().get();

    long deadline = System.currentTimeMillis() + 10_000;
    while (!activity.isFinishing() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    assertTrue("the voice search activity never finished", activity.isFinishing());
    assertNull(
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity());
  }

  @Test
  public void aVoiceMatchOnASongPlaysThatSong() {
    long[] songs =
        ShortcutActivity.songListForVoiceMatch(
            ApplicationProvider.getApplicationContext(),
            new com.andrew.apollo.model.Song(7, "A song", "An artist", "An album", 180));
    org.junit.Assert.assertArrayEquals(new long[] {7}, songs);
  }

  @Test
  public void anUnknownShortcutTypeHasNoSongs() {
    assertNull(
        ShortcutActivity.songListForShortcut(
            ApplicationProvider.getApplicationContext(), "text/plain", 1));
  }
}
