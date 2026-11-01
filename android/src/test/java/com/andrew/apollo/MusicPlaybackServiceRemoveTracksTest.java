/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Looper;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Deleting songs took them out of the queue one id at a time: when the playing song was deleted,
 * the service opened the next one even if it was being deleted too, and each removal sent its own
 * queue change (GitHub #1450). The queue now drops them all at once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceRemoveTracksTest {

  @Before
  public void installPlatform() {
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
  public void everyInstanceIsRemovedAndTheRestKeepTheirOrder() {
    long[] queue = {1, 2, 3, 2, 4, 5};
    MusicPlaybackService.QueueRemoval removal =
        MusicPlaybackService.compactQueue(queue, queue.length, 4, new long[] {3, 2});

    assertEquals(3, removal.removed);
    assertEquals(3, removal.length);
    assertArrayEquals(new long[] {1, 4, 5}, Arrays.copyOf(queue, removal.length));
    assertEquals("the playing song (4) keeps playing", 1, removal.position);
    assertFalse(removal.playingRemoved);
  }

  @Test
  public void playbackMovesPastEveryRemovedSongAtOnce() {
    long[] queue = {1, 2, 3, 4};
    MusicPlaybackService.QueueRemoval removal =
        MusicPlaybackService.compactQueue(queue, queue.length, 1, new long[] {2, 3});

    assertTrue(removal.playingRemoved);
    assertEquals("the next song that stays (4), not the deleted 3", 4L, queue[removal.position]);
  }

  @Test
  public void removingTheLastSongsLeavesThePositionAtTheEnd() {
    long[] queue = {1, 2, 3};
    MusicPlaybackService.QueueRemoval removal =
        MusicPlaybackService.compactQueue(queue, queue.length, 2, new long[] {3});

    assertTrue(removal.playingRemoved);
    assertEquals(2, removal.length);
    assertEquals("past the end; the service wraps to 0", 2, removal.position);
  }

  @Test
  public void theServiceRemovesTheDeletedSongsFromItsQueue() throws Exception {
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    set(service, "mPlayList", new long[] {7, 8, 9, 8, 10});
    set(service, "mPlayListLen", 5);
    set(service, "mPlayPos", 4);

    assertEquals(3, service.removeTracks(new long[] {8, 9}));

    assertArrayEquals(new long[] {7, 10}, service.getQueue());
    assertEquals(1, service.getQueuePosition());
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = MusicPlaybackService.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
