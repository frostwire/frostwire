/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Looper;
import android.widget.ArrayAdapter;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.model.Song;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** MusicUtils starting playback: from lists of ids, from files and from the Apollo screens. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class MusicUtilsPlaybackTest {

  private ApolloServiceFixture fixture;
  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    fixture = ApolloServiceFixture.start();
    ApolloServiceFixture.installPlatform(false);
    FakeMediaStore.install();
    FakeConfig.install();
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
    FakeConfig.uninstall();
    ApolloServiceFixture.noService();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void playingAListOpensItAndStartsPlayingFromThePosition() {
    MusicUtils.playFDs(new long[] {4, 5, 6}, 1, false);
    idle();

    assertArrayEquals(new long[] {4, 5, 6}, MusicUtils.getQueue());
    assertEquals(5, MusicUtils.getCurrentAudioId());
    assertTrue(MusicUtils.isPlaying());
    assertFalse(MusicUtils.isShuffleEnabled());
  }

  @Test
  public void playingTheSameListFromTheSamePositionKeepsTheQueueAndResumes() {
    MusicUtils.playFDs(new long[] {4, 5, 6}, 1, false);
    idle();
    MusicUtils.pause();
    idle();
    assertFalse(MusicUtils.isPlaying());

    MusicUtils.playFDs(new long[] {4, 5, 6}, 1, false);
    idle();

    assertTrue("it resumed", MusicUtils.isPlaying());
    assertEquals(1, fixture.service.getQueuePosition());
  }

  @Test
  public void forcingShuffleTurnsItOnAndANegativePositionStartsAtTheFirstSong() {
    MusicUtils.playFDs(new long[] {4, 5, 6}, -1, true);
    idle();

    assertTrue(MusicUtils.isShuffleEnabled());
    assertTrue(MusicUtils.isPlaying());
    assertEquals(3, MusicUtils.getQueue().length);
  }

  @Test
  public void playNextAndAddToQueueEnqueueAfterTheCurrentAndAtTheEnd() {
    MusicUtils.playFDs(new long[] {1, 2, 3}, 0, false);
    idle();

    MusicUtils.playNext(new long[] {9});
    idle();
    assertArrayEquals(new long[] {1, 9, 2, 3}, MusicUtils.getQueue());

    // The confirmation toast keeps the main looper busy, so this one is not idled.
    MusicUtils.addToQueue(context, new long[] {8});
    assertArrayEquals(new long[] {1, 9, 2, 3, 8}, MusicUtils.getQueue());
    MusicUtils.addToQueue(null, new long[] {7});
    MusicUtils.addToQueue(context, null);
    assertEquals(5, MusicUtils.getQueue().length);
  }

  @Test
  public void shuffleAllOpensEverySongInMediaStoreShuffled() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 11L), row("_id", 12L));

    MusicUtils.shuffleAll(context);
    idle();

    assertTrue(MusicUtils.isShuffleEnabled());
    assertEquals(2, MusicUtils.getQueue().length);
    assertTrue(MusicUtils.isPlaying());
  }

  @Test
  public void shuffleAllWithNoSongsDoesNothing() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    MusicUtils.shuffleAll(context);
    idle();

    assertEquals(0, MusicUtils.getQueue().length);
  }

  @Test
  public void playingAFileHandsItToTheServiceFromAPathOrAUri() {
    assertTrue(MusicUtils.playFile(new File("/storage/emulated/0/a.mp3")));
    assertTrue(MusicUtils.playFileFromUri(android.net.Uri.parse("content://x/y/1")));
    idle();
  }

  @Test
  public void clickingAFileWithAMediaStoreIdPlaysItFromTheQueue() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 77L));

    MusicUtils.playFileFromUserItemClick(context, new File("/storage/emulated/0/a.mp3"));
    idle();

    assertArrayEquals(new long[] {77}, MusicUtils.getQueue());
    assertTrue(MusicUtils.isPlaying());
  }

  @Test
  public void clickingAFileUnknownToMediaStoreStillPlaysItAsAFile() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();

    MusicUtils.playFileFromUserItemClick(activity, new File("/storage/emulated/0/a.mp3"));
    idle();

    assertEquals("it plays as a one-file queue", 1, fixture.player.getMediaItemCount());
  }

  @Test
  public void playingAnArtistAlbumPlaylistFavoritesOrLastAddedPlaysTheirSongs() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            u.getPath().contains("members")
                ? cursor(p, row("audio_id", 41L))
                : cursor(p, row("_id", 21L), row("_id", 22L));

    MusicUtils.playArtist(context, 1, 0);
    idle();
    assertArrayEquals(new long[] {21, 22}, MusicUtils.getQueue());

    MusicUtils.playAlbum(context, 2, 1);
    idle();
    assertEquals(22, MusicUtils.getCurrentAudioId());

    MusicUtils.playPlaylist(context, 3);
    idle();
    assertArrayEquals(new long[] {41}, MusicUtils.getQueue());

    MusicUtils.playLastAdded(context);
    idle();
    assertArrayEquals(new long[] {21, 22}, MusicUtils.getQueue());

    MusicUtils.playFavorites(context);
    idle();
    assertArrayEquals("nothing is a favorite yet", new long[] {21, 22}, MusicUtils.getQueue());
  }

  @Test
  public void clickingASongInAListPlaysTheWholeListFromThatSong() {
    ArrayAdapter<Song> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
    adapter.add(new Song(10, "a", "x", "y", 1));
    adapter.add(new Song(20, "b", "x", "y", 1));
    adapter.add(new Song(30, "c", "x", "y", 1));
    final AtomicInteger ready = new AtomicInteger();

    MusicUtils.playAllFromUserItemClick(adapter, 1, ready::incrementAndGet);
    idle();

    assertArrayEquals(new long[] {10, 20, 30}, MusicUtils.getQueue());
    assertEquals(20, MusicUtils.getCurrentAudioId());
    assertEquals(1, ready.get());
    MusicUtils.playAllFromUserItemClick(adapter, 2);
    idle();
    assertEquals(30, MusicUtils.getCurrentAudioId());
  }

  @Test
  public void clickingInAnEmptyListStartsNothing() {
    ArrayAdapter<Song> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);

    MusicUtils.playAllFromUserItemClick(adapter, 0, null);
    idle();

    assertEquals(0, MusicUtils.getQueue().length);
  }

  @Test
  public void clickingASongWhileTheServiceIsDownStartsTheServiceAndPlaysOnceBound()
      throws Exception {
    ApolloServiceFixture.noService();
    shadowOf((Application) context)
        .setComponentNameAndServiceForBindService(
            new ComponentName(context, MusicPlaybackService.class), new Binder());
    ArrayAdapter<Song> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
    adapter.add(new Song(10, "a", "x", "y", 1));

    MusicUtils.playAllFromUserItemClick(adapter, 0, null);
    idle();

    Intent started = shadowOf((Application) context).getNextStartedService();
    assertNotNull("the service was asked to start", started);
    assertEquals(MusicPlaybackService.SERVICECMD, started.getAction());
  }

  @Test
  public void bindingToTheServiceRunsEveryWaitingCallbackOnceItIsConnected() throws Exception {
    final AtomicInteger first = new AtomicInteger();
    final AtomicInteger second = new AtomicInteger();
    shadowOf((Application) context)
        .setComponentNameAndServiceForBindService(
            new ComponentName(context, MusicPlaybackService.class), new Binder());
    Intent intent = MusicUtils.buildStartMusicPlaybackServiceIntent(context);

    MusicUtils.startMusicPlaybackService(context, intent, first::incrementAndGet);
    MusicUtils.startMusicPlaybackService(context, intent, second::incrementAndGet);
    idle();

    assertEquals(1, first.get());
    assertTrue("a second caller is not lost: " + second.get(), second.get() <= 1);
    assertTrue(MusicUtils.isMusicPlaybackServiceRunning());
  }

  @Test
  public void startingWithoutACallbackJustStartsTheService() {
    MusicUtils.startMusicPlaybackService(
        context, MusicUtils.buildStartMusicPlaybackServiceIntent(context), null);

    assertNotNull(shadowOf((Application) context).getNextStartedService());
  }

  @Test
  public void goingToTheForegroundAndBackTellsARunningService() {
    MusicUtils.notifyForegroundStateChanged(context, true);
    MusicUtils.notifyForegroundStateChanged(context, true);
    MusicUtils.notifyForegroundStateChanged(context, false);
    MusicUtils.notifyForegroundStateChanged(context, false);
    idle();

    assertTrue(MusicUtils.isMusicPlaybackServiceRunning());
  }

  // --- deleting tracks ---

  @Test
  public void deletingTracksRemovesThemFromTheQueueTheStoreAndEveryPlaylist() {
    MusicUtils.playFDs(new long[] {1, 2, 3}, 0, false);
    idle();
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> {
          if (u.getPath().contains("playlists")) {
            return cursor(p, row("_id", 5L, "name", "Mix"));
          }
          return cursor(p, row("_id", 2L, "_data", "/nowhere/2.mp3", "album_id", 1L));
        };

    MusicUtils.deleteTracks(context, new long[] {2}, true);

    assertArrayEquals(new long[] {1, 3}, MusicUtils.getQueue());
    boolean mediaDeleted = false;
    boolean memberDeleted = false;
    for (String d : FakeMediaStore.deletes) {
      mediaDeleted |= d.contains("audio/media") && d.contains("_id IN (2)");
      memberDeleted |= d.contains("/5/members") && d.endsWith("audio_id IN (2)");
    }
    assertTrue("the song is deleted from the store: " + FakeMediaStore.deletes, mediaDeleted);
    assertTrue("and from the playlist: " + FakeMediaStore.deletes, memberDeleted);
  }

  @Test
  public void deletingWithoutAContextOrIdsDoesNothing() {
    MusicUtils.deleteTracks(null, new long[] {2}, false);
    MusicUtils.deleteTracks(context, null, false);
    MusicUtils.deleteTracks(context, null);
    MusicUtils.deleteTracks((Activity) null, new long[] {1}, false, null, null);
    MusicUtils.deleteTracks(
        Robolectric.buildActivity(Activity.class).setup().get(), new long[0], false, null, null);

    assertTrue(FakeMediaStore.deletes.isEmpty());
  }

  @Test
  public void aPendingDeleteSurvivesRecreationAndIsSettledByTheActivityResult() {
    Bundle state = new Bundle();
    MusicUtils.savePendingTrackDelete(null);
    MusicUtils.savePendingTrackDelete(state);
    assertFalse(
        "nothing pending, nothing saved",
        state.containsKey("com.andrew.apollo.pendingTrackDelete.ids"));

    Bundle saved = new Bundle();
    saved.putLongArray("com.andrew.apollo.pendingTrackDelete.ids", new long[] {7, 8});
    saved.putBoolean("com.andrew.apollo.pendingTrackDelete.notify", true);
    MusicUtils.restorePendingTrackDelete(null, saved);
    MusicUtils.restorePendingTrackDelete(context, null);
    MusicUtils.restorePendingTrackDelete(context, new Bundle());
    MusicUtils.restorePendingTrackDelete(context, saved);
    MusicUtils.restorePendingTrackDelete(context, saved);

    Bundle out = new Bundle();
    MusicUtils.savePendingTrackDelete(out);
    assertArrayEquals(
        new long[] {7, 8}, out.getLongArray("com.andrew.apollo.pendingTrackDelete.ids"));
    assertTrue(out.getBoolean("com.andrew.apollo.pendingTrackDelete.notify"));

    assertFalse("some other request", MusicUtils.handleDeleteTracksActivityResult(null, 1, -1));
    assertTrue(
        "the user said no",
        MusicUtils.handleDeleteTracksActivityResult(null, 0x4155, Activity.RESULT_CANCELED));
    assertTrue(
        "and nothing is pending any more",
        MusicUtils.handleDeleteTracksActivityResult(null, 0x4155, Activity.RESULT_OK));
    Bundle after = new Bundle();
    MusicUtils.savePendingTrackDelete(after);
    assertFalse(after.containsKey("com.andrew.apollo.pendingTrackDelete.ids"));
  }
}
