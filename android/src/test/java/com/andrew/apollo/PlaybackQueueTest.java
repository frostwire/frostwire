/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.FakeMediaSourceFactory;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The queue is ExoPlayer's playlist (GitHub #1452), driven here through a real ExoPlayer with fake
 * media: what the app's buttons and a Bluetooth headset do lands on the same song, and the playlist
 * never holds more than the queue.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PlaybackQueueTest {

  private ExoPlayer player;
  private PlaybackQueue queue;
  private final List<String> events = new ArrayList<>();

  @Before
  public void setUp() {
    player =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(new FakeMediaSourceFactory())
            .build();
    queue =
        new PlaybackQueue(
            player,
            new PlaybackQueue.Listener() {
              @Override
              public void onQueueChanged() {
                events.add("queue");
              }

              @Override
              public void onSongChanged(long audioId) {
                events.add("song " + audioId);
              }
            });
  }

  @After
  public void tearDown() {
    player.release();
  }

  /**
   * Controllers (Android Auto, Wear, car screens) show the whole queue; every song in it carries
   * its title, artist and album, not only the one playing (GitHub #1452).
   */
  @Test
  public void everyQueuedSongCarriesItsTitle() {
    queue.setMetadataLookup(
        ids -> {
          java.util.Map<Long, androidx.media3.common.MediaMetadata> found =
              new java.util.HashMap<>();
          for (long id : ids) {
            found.put(
                id,
                new androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("song " + id)
                    .setArtist("artist " + id)
                    .build());
          }
          return found;
        });

    queue.open(new long[] {10, 20}, 0, 0);
    queue.add(new long[] {30}, false);

    assertEquals("song 20", String.valueOf(player.getMediaItemAt(1).mediaMetadata.title));
    assertEquals("artist 30", String.valueOf(player.getMediaItemAt(2).mediaMetadata.artist));
  }

  @Test
  public void withoutALookupTheQueueStillWorks() {
    queue.open(new long[] {10, 20}, 0, 0);

    assertEquals(2, player.getMediaItemCount());
    assertEquals("20", player.getMediaItemAt(1).mediaId);
  }

  @Test
  public void nextAndPreviousWrapAroundARepeatingQueue() {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    queue.open(new long[] {10, 20, 30}, 0, 0);

    queue.next();
    assertCurrent(20);
    queue.next();
    assertCurrent(30);
    queue.next();
    assertCurrent(10);
    queue.previous();
    assertCurrent(30);
    queue.previous();
    assertCurrent(20);
  }

  @Test
  public void previousFromTheFirstSongOfARepeatingQueuePlaysTheLastOne() {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    queue.open(new long[] {10, 20, 30}, 0, 0);
    events.clear();

    queue.previous();

    assertCurrent(30);
    assertEquals(2, queue.snapshot().index());
    assertEquals(List.of("song 30"), events);
  }

  @Test
  public void withoutRepeatNextStopsAtTheEnd() {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
    queue.open(new long[] {10, 20}, 1, 0);

    queue.next();

    assertCurrent(20);
  }

  @Test
  public void songsPlayingThroughNeverGrowThePlaylist() throws Exception {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    queue.open(new long[] {10, 20, 30}, 0, 0);
    events.clear();
    player.play();

    long[] expected = {20, 30, 10, 20, 30, 10, 20};
    for (long id : expected) {
      TestPlayerRunHelper.advance(player)
          .untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
      assertEquals("the playlist is the queue", 3, player.getMediaItemCount());
      assertArrayEquals(new long[] {10, 20, 30}, queue.snapshot().ids());
      assertCurrent(id);
    }
    assertTrue("every song that started was told", events.contains("song 30"));
    assertEquals(expected.length, events.size());
  }

  @Test
  public void openingTheQueueAgainOnlyMovesToTheSong() {
    queue.open(new long[] {1, 2, 3}, 0, 0);
    events.clear();

    queue.open(new long[] {1, 2, 3}, 2, 0);

    assertCurrent(3);
    assertEquals(List.of("song 3"), events);
  }

  @Test
  public void openingAnotherListStartingWithTheSameSongIsNoNewSong() {
    queue.open(new long[] {1, 2, 3}, 0, 0);
    events.clear();

    queue.open(new long[] {1, 5}, 0, 0);

    assertArrayEquals(new long[] {1, 5}, queue.snapshot().ids());
    assertEquals(List.of("queue"), events);
  }

  @Test
  public void addedSongsGoNextOrLast() {
    queue.open(new long[] {1, 2, 3}, 1, 0);
    events.clear();

    assertEquals(2, queue.add(new long[] {7, 8}, true));
    assertEquals(5, queue.add(new long[] {9}, false));

    assertArrayEquals(new long[] {1, 2, 7, 8, 3, 9}, queue.snapshot().ids());
    assertCurrent(2);
    assertEquals(List.of("queue", "queue"), events);
    queue.next();
    assertCurrent(7);
  }

  @Test
  public void songsAddedToAnEmptyQueueAreReadyToPlay() {
    queue.add(new long[] {4, 5}, true);

    assertArrayEquals(new long[] {4, 5}, queue.snapshot().ids());
    assertCurrent(4);
    assertEquals(List.of("queue", "song 4"), events);
    assertTrue(player.getPlaybackState() != Player.STATE_IDLE);
  }

  @Test
  public void withShuffleOnSongsAddedNextPlayNextAndTheRestAfterTheCurrentSong() {
    queue.open(new long[] {1, 2, 3, 4, 5, 6}, 3, 0);
    queue.setShuffle(true);

    queue.add(new long[] {7, 8}, false);
    queue.add(new long[] {9}, true);

    List<Long> after = songsAfterCurrent();
    assertEquals("added next plays next", 9L, (long) after.get(0));
    List<Long> rest = new ArrayList<>(after.subList(1, after.size()));
    rest.sort(Long::compare);
    assertEquals("all the others after it", List.of(1L, 2L, 3L, 5L, 6L, 7L, 8L), rest);
    queue.next();
    assertCurrent(9);
  }

  @Test
  public void movingASongKeepsTheCurrentSongCurrent() {
    queue.open(new long[] {1, 2, 3, 4}, 0, 0);
    events.clear();

    queue.move(0, 3);

    assertArrayEquals(new long[] {2, 3, 4, 1}, queue.snapshot().ids());
    assertCurrent(1);
    assertEquals(3, queue.snapshot().index());
    assertEquals(List.of("queue"), events);
    queue.move(10, -4);
    assertArrayEquals("both ends are clamped", new long[] {1, 2, 3, 4}, queue.snapshot().ids());
  }

  @Test
  public void removingThePlayingSongMovesStraightToTheFirstOneThatStays() {
    queue.open(new long[] {1, 2, 3, 2, 4}, 1, 0);
    events.clear();

    assertEquals(3, queue.remove(new long[] {2, 3}));

    assertArrayEquals(new long[] {1, 4}, queue.snapshot().ids());
    assertCurrent(4);
    assertEquals("one change each", List.of("queue", "song 4"), events);
  }

  @Test
  public void removingOtherSongsKeepsTheSongPlaying() {
    queue.open(new long[] {1, 2, 3, 4}, 2, 0);
    events.clear();

    assertEquals(2, queue.remove(new long[] {1, 4, 99}));

    assertArrayEquals(new long[] {2, 3}, queue.snapshot().ids());
    assertCurrent(3);
    assertEquals(List.of("queue"), events);
  }

  @Test
  public void removingThePlayingLastSongWrapsToTheFirstAndKeepsPlaying() throws Exception {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
    queue.open(new long[] {1, 2, 3}, 2, 0);
    TestPlayerRunHelper.play(player).untilState(Player.STATE_READY);

    queue.removeRange(2, 2);

    assertCurrent(1);
    TestPlayerRunHelper.advance(player).untilState(Player.STATE_READY);
    assertTrue(player.isPlaying());
  }

  @Test
  public void removingEverySongEmptiesTheQueue() {
    queue.open(new long[] {7, 8, 9}, 1, 0);
    events.clear();

    assertEquals("clamped at both ends", 3, queue.removeRange(-1, 10));

    assertEquals(0, queue.snapshot().size());
    assertEquals(-1, queue.snapshot().audioId());
    assertEquals(-1, queue.snapshot().index());
    assertEquals(List.of("queue", "song -1"), events);
  }

  @Test
  public void removingAllButTheCurrentSongKeepsItPlaying() {
    queue.open(new long[] {7, 8, 9}, 1, 0);
    events.clear();

    assertEquals(2, queue.removeAllButCurrent());

    assertArrayEquals(new long[] {8}, queue.snapshot().ids());
    assertEquals(List.of("queue"), events);
  }

  @Test
  public void shuffleOnAndOffKeepsTheCurrentSongAndShufflePlaysEveryOtherSongAfterIt() {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
    queue.open(new long[] {1, 2, 3, 4, 5, 6, 7, 8}, 5, 0);

    queue.setShuffle(true);

    assertCurrent(6);
    assertTrue(player.getShuffleModeEnabled());
    assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 7L, 8L), new HashSet<>(songsAfterCurrent()));
    queue.next();
    long shuffled = queue.snapshot().audioId();
    queue.setShuffle(false);
    assertCurrent(shuffled);
    assertTrue(!player.getShuffleModeEnabled());
  }

  @Test
  public void playingAFinishedQueueStartsOver() throws Exception {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
    queue.open(new long[] {1, 2}, 1, 0);
    TestPlayerRunHelper.play(player).untilState(Player.STATE_ENDED);
    events.clear();

    queue.restartIfEnded();

    assertCurrent(1);
    assertEquals(List.of("song 1"), events);
    TestPlayerRunHelper.advance(player).untilState(Player.STATE_READY);
    queue.restartIfEnded();
    assertEquals("only a finished queue starts over", List.of("song 1"), events);
  }

  @Test
  public void theSnapshotCountsWhatARemovalWouldTake() {
    queue.open(new long[] {1, 2, 3, 2}, 0, 0);
    PlaybackQueue.Snapshot snapshot = queue.snapshot();

    assertEquals(2, snapshot.count(new long[] {2}));
    assertEquals(3, snapshot.count(new long[] {2, 3, 9}));
    assertEquals(0, snapshot.count(null));
    assertEquals(4, snapshot.countRange(-1, 10));
    assertEquals(2, snapshot.countRange(1, 2));
    assertEquals(0, snapshot.countRange(3, 1));
  }

  @Test
  public void aNegativeStartPlaysTheFirstSongInPlaybackOrder() {
    queue.open(new long[] {1, 2, 3}, -1, 0);

    assertCurrent(1);
  }

  @Test
  public void snapshotReadOnAnotherThreadMatchesThePlayer() throws Exception {
    queue.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    List<Runnable> mutations =
        List.of(
            () -> queue.open(new long[] {1, 2, 3, 4}, 1, 0),
            () -> queue.add(new long[] {5}, true),
            () -> queue.add(new long[] {6}, false),
            () -> queue.move(0, 4),
            queue::next,
            queue::previous,
            queue::previous,
            () -> queue.setShuffle(true),
            queue::next,
            () -> queue.remove(new long[] {queue.snapshot().audioId()}),
            () -> queue.removeRange(0, 1),
            () -> queue.seekTo(0),
            () -> queue.setShuffle(false),
            () -> queue.removeRange(0, 10));
    for (Runnable mutation : mutations) {
      mutation.run();
      long[] playerIds = new long[player.getMediaItemCount()];
      for (int i = 0; i < playerIds.length; i++) {
        playerIds[i] = Long.parseLong(player.getMediaItemAt(i).mediaId);
      }
      AtomicReference<PlaybackQueue.Snapshot> seen = new AtomicReference<>();
      Thread reader = new Thread(() -> seen.set(queue.snapshot()));
      reader.start();
      reader.join();
      assertArrayEquals(playerIds, seen.get().ids());
      assertEquals(
          playerIds.length == 0 ? C.INDEX_UNSET : player.getCurrentMediaItemIndex(),
          seen.get().index());
    }
  }

  @Test
  public void repeatModesMapBothWays() {
    for (int mode :
        new int[] {
          MusicPlaybackService.REPEAT_NONE,
          MusicPlaybackService.REPEAT_CURRENT,
          MusicPlaybackService.REPEAT_ALL
        }) {
      queue.setRepeatMode(mode);
      assertEquals(mode, PlaybackQueue.toApolloRepeatMode(player.getRepeatMode()));
    }
    assertEquals(Player.REPEAT_MODE_ALL, player.getRepeatMode());
  }

  @Test
  public void aFileMediaStoreDoesNotKnowHasNoAudioId() {
    queue.openUri(Uri.parse("file:///storage/emulated/0/Download/a.mp3"));

    assertEquals(1, queue.snapshot().size());
    assertEquals(-1, queue.snapshot().audioId());
    assertEquals(42, PlaybackQueue.audioIdOf(PlaybackQueue.mediaItemOf(42)));
    assertEquals(-1, PlaybackQueue.audioIdOf(MediaItem.fromUri("file:///a.mp3")));
    assertEquals(-1, PlaybackQueue.audioIdOf(null));
  }

  private void assertCurrent(long audioId) {
    assertEquals(audioId, Long.parseLong(player.getCurrentMediaItem().mediaId));
    assertEquals(audioId, queue.snapshot().audioId());
    assertEquals(player.getCurrentMediaItemIndex(), queue.snapshot().index());
  }

  /** The songs after the current one in playback order, without repeating. */
  private List<Long> songsAfterCurrent() {
    Timeline timeline = player.getCurrentTimeline();
    boolean shuffle = player.getShuffleModeEnabled();
    List<Long> ids = new ArrayList<>();
    for (int i =
            timeline.getNextWindowIndex(
                player.getCurrentMediaItemIndex(), Player.REPEAT_MODE_OFF, shuffle);
        i != C.INDEX_UNSET;
        i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)) {
      ids.add(Long.parseLong(player.getMediaItemAt(i).mediaId));
    }
    return ids;
  }
}
