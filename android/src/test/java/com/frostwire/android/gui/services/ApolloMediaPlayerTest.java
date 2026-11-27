/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.EphemeralPlaylist;
import com.frostwire.android.core.player.PlaylistItem;
import com.frostwire.android.gui.Librarian;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ApolloMediaPlayerTest {
  private final ApolloMediaPlayer player = new ApolloMediaPlayer();
  private MockedStatic<MusicUtils> music;

  @Before
  public void setUp() {
    music = mockStatic(MusicUtils.class);
  }

  @After
  public void tearDown() {
    music.close();
  }

  private static FWFileDescriptor fd(int id, String path) {
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.id = id;
    fd.filePath = path;
    return fd;
  }

  @Test
  public void anEmptyPlaylistPlaysNothing() {
    player.play(new EphemeralPlaylist(new ArrayList<>()));
    music.verifyNoInteractions();
  }

  @Test
  public void aSingleFileNotInTheMediaStoreIsPlayedStraightFromDisk() {
    List<FWFileDescriptor> one = new ArrayList<>();
    one.add(fd(-1, "/x/song.mp3"));
    player.play(new EphemeralPlaylist(one));
    music.verify(() -> MusicUtils.playFile(new File("/x/song.mp3")));
    music.verify(() -> MusicUtils.playFDs(any(), anyInt(), anyBoolean()), never());
  }

  @Test
  public void aPlaylistOfMediaStoreFilesStartsAtTheCurrentItem() {
    List<FWFileDescriptor> fds = new ArrayList<>();
    fds.add(fd(10, "/a.mp3"));
    fds.add(fd(20, "/b.mp3"));
    fds.add(fd(30, "/c.mp3"));
    EphemeralPlaylist playlist = new EphemeralPlaylist(fds);
    playlist.setNextItem(new PlaylistItem(fds.get(1)));
    music.when(MusicUtils::isShuffleEnabled).thenReturn(true);
    player.play(playlist);
    ArgumentCaptor<long[]> ids = ArgumentCaptor.forClass(long[].class);
    music.verify(() -> MusicUtils.playFDs(ids.capture(), eq(1), eq(true)));
    assertArrayEquals(new long[] {10, 20, 30}, ids.getValue());
  }

  @Test
  public void aSingleMediaStoreFileDoesNotForceShuffle() {
    List<FWFileDescriptor> fds = new ArrayList<>();
    fds.add(fd(10, "/a.mp3"));
    music.when(MusicUtils::isShuffleEnabled).thenReturn(true);
    player.play(new EphemeralPlaylist(fds));
    ArgumentCaptor<long[]> ids = ArgumentCaptor.forClass(long[].class);
    music.verify(() -> MusicUtils.playFDs(ids.capture(), eq(0), eq(false)));
    assertArrayEquals(new long[] {10}, ids.getValue());
  }

  @Test
  public void stoppingStopsThePlaybackServiceWhenThereIsOne() {
    MusicPlaybackService service = mock(MusicPlaybackService.class);
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(service);
    player.stop();
    verify(service).stop();
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(null);
    player.stop();
    music.when(MusicUtils::getMusicPlaybackService).thenThrow(new IllegalStateException("gone"));
    player.stop();
  }

  @Test
  public void playingStateComesFromTheMusicUtils() {
    assertFalse(player.isPlaying());
    music.when(MusicUtils::isPlaying).thenReturn(true);
    assertTrue(player.isPlaying());
  }

  @Test
  public void theCurrentFileIsLookedUpInTheMediaStoreOnceAndRemembered() {
    Application app = ApplicationProvider.getApplicationContext();
    FWFileDescriptor found = fd(77, "/found.mp3");
    Librarian librarian = mock(Librarian.class);
    when(librarian.getFileDescriptor(app, Constants.FILE_TYPE_AUDIO, 77)).thenReturn(found);
    music.when(MusicUtils::getCurrentAudioId).thenReturn(77L);
    try (MockedStatic<Librarian> librarians = mockStatic(Librarian.class)) {
      librarians.when(Librarian::instance).thenReturn(librarian);
      assertSame(found, player.getCurrentFD(app));
      assertSame(found, player.getCurrentFD(app));
      verify(librarian, times(1)).getFileDescriptor(app, Constants.FILE_TYPE_AUDIO, 77);
    }
  }

  @Test
  public void noCurrentFileMeansNullEvenWhenTheServiceFails() {
    Application app = ApplicationProvider.getApplicationContext();
    music.when(MusicUtils::getCurrentAudioId).thenReturn(-1L);
    assertNull(player.getCurrentFD(app));
    music.when(MusicUtils::getCurrentAudioId).thenReturn(5L);
    assertNull("no context, no media store lookup", player.getCurrentFD(null));
    music.when(MusicUtils::getCurrentAudioId).thenThrow(new IllegalStateException("gone"));
    assertNull(player.getCurrentFD(app));
  }

  @Test
  public void theSimplePlayerFileIsAnAudioIdLookupInTheRingtones() {
    Application app = ApplicationProvider.getApplicationContext();
    FWFileDescriptor ringtone = fd(5, "/ring.mp3");
    Librarian librarian = mock(Librarian.class);
    when(librarian.getFileDescriptor(app, Constants.FILE_TYPE_RINGTONES, 5)).thenReturn(ringtone);
    music.when(MusicUtils::getCurrentSimplePlayerAudioId).thenReturn(5L);
    try (MockedStatic<Librarian> librarians = mockStatic(Librarian.class)) {
      librarians.when(Librarian::instance).thenReturn(librarian);
      assertSame(ringtone, player.getSimplePlayerCurrentFD(app));
      assertNull(player.getSimplePlayerCurrentFD(null));
      when(librarian.getFileDescriptor(any(), anyByte(), anyInt()))
          .thenThrow(new IllegalStateException("gone"));
      assertNull(player.getSimplePlayerCurrentFD(app));
    }
  }
}
