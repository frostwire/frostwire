/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.net.Uri;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * When ExoPlayer moved to its next or previous item, the service guessed the new queue position
 * instead of reading which song ExoPlayer was on (GitHub #1452). Going back past the first song of
 * a repeat-all queue left the position at 0 while the last song played, and after the queue was
 * edited the queued-next index could point at another song, so the title, notification and Recent
 * named the wrong song.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceTransitionTest {

  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    service = Robolectric.buildService(MusicPlaybackService.class).create().get();
    set("mPlayList", new long[] {10, 20, 30});
    set("mPlayListLen", 3);
    set("mShuffleEnabled", false);
  }

  @Test
  public void goingBackPastTheFirstSongOfARepeatingQueuePlaysTheLastOne() throws Exception {
    set("mRepeatMode", MusicPlaybackService.REPEAT_ALL);
    set("mPlayPos", 0);

    assertEquals(2, service.positionAfterPrevious(30));
  }

  @Test
  public void goingBackFollowsTheSongExoPlayerIsOn() throws Exception {
    set("mPlayPos", 2);

    assertEquals(1, service.positionAfterPrevious(20));
    assertEquals("a song no longer queued: the usual guess", 1, service.positionAfterPrevious(99));
  }

  @Test
  public void advancingFollowsTheSongExoPlayerIsOnAfterTheQueueChanged() throws Exception {
    set("mPlayPos", 0);
    set("mNextPlayPos", 1); // queued when the queue was {10, 20, 30}
    set("mPlayList", new long[] {10, 30, 20}); // 20 and 30 swapped since

    assertEquals(2, service.positionAfterNext(20));
  }

  @Test
  public void theSongIdIsReadFromTheMediaItem() {
    assertEquals(
        42L,
        MusicPlaybackService.audioIdOf(
            MediaItem.fromUri(Uri.parse("content://media/external_primary/audio/media/42"))));
    assertEquals(-1L, MusicPlaybackService.audioIdOf(MediaItem.fromUri("file:///music/a.mp3")));
    assertEquals(-1L, MusicPlaybackService.audioIdOf(null));
  }

  private void set(String name, Object value) throws Exception {
    Field field = MusicPlaybackService.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(service, value);
  }
}
