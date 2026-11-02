/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.os.Looper;
import android.provider.BaseColumns;
import android.provider.MediaStore.Audio.AudioColumns;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.provider.RecentStore;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * After a song advanced on its own, its duration stayed -1 (broken seek bar) and it never reached
 * Recent, which drops songs without a duration (GitHub #1451): a gapless transition stays READY, so
 * the only place that read the duration never ran, and Recent was written before the new duration
 * was known.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceDurationTest {

  @Test
  public void recentRecordsMediaStoresDurationFirst() {
    assertEquals(215_000L, MusicPlaybackService.recentDuration(215_000L, -1L));
    assertEquals(215_000L, MusicPlaybackService.recentDuration(215_000L, 180_000L));
    assertEquals(180_000L, MusicPlaybackService.recentDuration(0L, 180_000L));
  }

  @Test
  public void exoPlayersUnknownDurationIsMinusOne() {
    assertEquals(-1L, MusicPlaybackService.MultiPlayer.knownDuration(C.TIME_UNSET));
    assertEquals(200_000L, MusicPlaybackService.MultiPlayer.knownDuration(200_000L));
  }

  /**
   * Plays a queue whose songs last different times on a real ExoPlayer with fake media, and checks
   * the song that started on its own: the seek bar's length, and that Recent recorded it.
   */
  @Test
  public void aSongThatStartsOnItsOwnHasItsDurationAndReachesRecent() throws Exception {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    Field configuration = ConfigurationManager.class.getDeclaredField("instance");
    configuration.setAccessible(true);
    configuration.set(null, Mockito.mock(ConfigurationManager.class));
    Field playerHandler = MusicPlaybackService.class.getDeclaredField("mPlayerHandler");
    playerHandler.setAccessible(true);
    playerHandler.set(null, new MusicPlaybackService.MusicPlayerHandler(Looper.getMainLooper()));
    Context context = ApplicationProvider.getApplicationContext();
    ExoPlayer player =
        new TestExoPlayerBuilder(context).setMediaSourceFactory(new SongsOfTheirLength()).build();
    MusicPlaybackService service =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    try {
      service.attachPlayer(player);
      service.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
      service.open(new long[] {5, 7}, 0);
      service.play();

      TestPlayerRunHelper.advance(player)
          .untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

      assertEquals(7, service.getAudioId());
      assertEquals("the seek bar's length", 7_000, service.duration());
      try (Cursor recent =
          RecentStore.getInstance(context)
              .getReadableDatabase()
              .query(
                  RecentStore.TABLE_NAME,
                  new String[] {AudioColumns.DURATION},
                  BaseColumns._ID + " = 7",
                  null,
                  null,
                  null,
                  null)) {
        assertTrue("song 7 is in Recent", recent.moveToFirst());
        assertEquals(7_000, recent.getLong(0));
      }
    } finally {
      player.release();
      playerHandler.set(null, null);
      configuration.set(null, null);
    }
  }

  /** Fake media where song n lasts n seconds. */
  private static final class SongsOfTheirLength implements MediaSource.Factory {
    @Override
    public MediaSource createMediaSource(MediaItem mediaItem) {
      long seconds = Long.parseLong(mediaItem.mediaId);
      return new FakeMediaSource(
          new FakeTimeline(
              new FakeTimeline.TimelineWindowDefinition.Builder()
                  .setMediaItem(mediaItem)
                  .setDurationUs(seconds * 1_000_000)
                  .build()));
    }

    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(DrmSessionManagerProvider provider) {
      return this;
    }

    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy policy) {
      return this;
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.CONTENT_TYPE_OTHER};
    }
  }
}
