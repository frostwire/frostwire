/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The service can die between the UI asking and the service answering: MusicUtils must keep the
 * screens alive and answer with the same defaults as when there is no service.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicUtilsServiceFailureTest {

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.installPlatform(false);
    FakeMediaStore.install();
    FakeConfig.install();
    ApolloServiceFixture.installFailingService();
  }

  @After
  public void tearDown() throws Exception {
    ApolloServiceFixture.noService();
    FakeConfig.uninstall();
  }

  @Test
  public void theQueriesAnswerWithTheDefaultsOfAMissingService() {
    assertTrue(MusicUtils.isMusicPlaybackServiceRunning());
    assertFalse(MusicUtils.isPlaying());
    assertTrue(MusicUtils.isStopped());
    assertFalse(MusicUtils.isShuffleEnabled());
    assertEquals(0, MusicUtils.getRepeatMode());
    assertNull(MusicUtils.getTrackName());
    assertNull(MusicUtils.getArtistName());
    assertNull(MusicUtils.getAlbumName());
    assertEquals(-1, MusicUtils.getCurrentAlbumId());
    assertEquals(-1, MusicUtils.getCurrentAudioId());
    assertEquals(-1, MusicUtils.getCurrentSimplePlayerAudioId());
    assertEquals(-1, MusicUtils.getCurrentArtistId());
    assertEquals(-1, MusicUtils.getAudioSessionId());
    assertEquals(0, MusicUtils.getQueue().length);
    assertEquals(0, MusicUtils.removeTrack(5));
    assertEquals(0, MusicUtils.removeTracks(new long[] {5}));
    assertEquals(0, MusicUtils.position());
    assertEquals(0, MusicUtils.duration());
    assertFalse(MusicUtils.isFavorite());
    assertNull(MusicUtils.getFilePath());
  }

  @Test
  public void everyCommandIsSwallowedWhenTheServiceFails() {
    MusicUtils.next();
    MusicUtils.previous();
    MusicUtils.play();
    MusicUtils.pause();
    MusicUtils.playPauseOrResume();
    shadowOf(Looper.getMainLooper()).idle();
    MusicUtils.cycleRepeat();
    MusicUtils.cycleShuffle();
    MusicUtils.seek(10);
    MusicUtils.setQueuePosition(1);
    MusicUtils.moveQueueItem(0, 1);
    MusicUtils.clearQueue();
    MusicUtils.toggleFavorite();
    MusicUtils.refresh();
    MusicUtils.playSimple("/a.mp3");
    MusicUtils.stopSimplePlayer();
    MusicUtils.playNext(new long[] {1});
    MusicUtils.addToQueue(context, new long[] {1});
    MusicUtils.playFDs(new long[] {1, 2}, 0, true);
    MusicUtils.shuffleAll(context);

    // Still answering: nothing was left half done, and the lookups work as before.
    assertFalse(MusicUtils.isPlaying());
    assertEquals(0, MusicUtils.getQueue().length);
  }

  @Test
  public void aFileHandedToAFailingServiceIsNotPlayed() {
    assertFalse(MusicUtils.playFile(new File("/a.mp3")));
    shadowOf(Looper.getMainLooper()).idle();
    assertFalse(MusicUtils.isPlaying());
  }
}
