/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.media3.common.C;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
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

  @Test
  public void aTransitionTakesTheNewItemsDurationInsteadOfForgettingIt() throws Exception {
    String source =
        new String(
                Files.readAllBytes(
                    Path.of(System.getProperty("user.dir"))
                        .resolve("apollo/src/com/andrew/apollo/MusicPlaybackService.java")),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(source.contains("mCachedPosition=0;mCachedDuration=-1;"));
    assertTrue(source.contains("publicvoidonTimelineChanged("));
  }
}
