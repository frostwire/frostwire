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
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.FakeMediaSourceFactory;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.DataStoreManager;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The service's queue is ExoPlayer's playlist (GitHub #1452): the app's buttons and a Bluetooth
 * headset or the lock screen, which drive ExoPlayer through the MediaSession, move through the same
 * queue and agree on the song; the playlist never grows past the queue; other threads read the
 * queue from a snapshot the player thread publishes.
 *
 * <p>The service runs with a real ExoPlayer playing fake media. Unless a test says otherwise its
 * player thread is the test's main thread, as ExoPlayer's Robolectric helpers need.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceQueueTest {

  private MusicPlaybackService service;
  private ExoPlayer player;
  private HandlerThread playerThread;
  private final List<ExoPlayer> players = new ArrayList<>();

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
    // Saving the modes as preferences goes through it; the app creates it at startup.
    setConfigurationManager(Mockito.mock(ConfigurationManager.class));
    service = Robolectric.buildService(MusicPlaybackService.class).create().get();
    setPlayerHandler(new MusicPlaybackService.MusicPlayerHandler(Looper.getMainLooper()));
    player = newPlayer();
    service.attachPlayer(player);
  }

  /** The handler is static: left set, later tests would post their work to this test's thread. */
  @After
  public void tearDown() throws Exception {
    for (ExoPlayer p : players) {
      if (p.getApplicationLooper() == Looper.getMainLooper()) {
        p.release();
      } else {
        new Handler(p.getApplicationLooper()).post(p::release);
      }
    }
    setPlayerHandler(null);
    setConfigurationManager(null);
    if (playerThread != null) {
      playerThread.quitSafely();
    }
  }

  @Test
  public void nextAndPreviousWrapAroundARepeatingQueueAndGetAudioIdAgrees() {
    service.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    service.open(new long[] {10, 20, 30}, 0);
    assertPlaying(10, 0);

    service.gotoNext();
    assertPlaying(20, 1);
    service.gotoNext();
    assertPlaying(30, 2);
    service.gotoNext();
    assertPlaying(10, 0);
    service.gotoPrev();
    assertPlaying(30, 2);
  }

  @Test
  public void theAppsNextAndAHeadsetsNextLandOnTheSameSong() {
    service.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    service.enableShuffle(true);
    service.open(new long[] {1, 2, 3, 4, 5, 6}, 2);

    service.gotoNext();
    long appsNext = service.getAudioId();
    service.gotoPrev();
    assertEquals(3, service.getAudioId());
    // A Bluetooth headset, the lock screen or Android Auto: the MediaSession calls ExoPlayer.
    player.seekToNext();

    assertEquals(appsNext, service.getAudioId());
    assertEquals(appsNext, Long.parseLong(player.getCurrentMediaItem().mediaId));
    player.seekToPrevious();
    assertEquals(3, service.getAudioId());
  }

  @Test
  public void theWholeQueueIsThePlaylistAndPlayingThroughItNeverGrowsIt() throws Exception {
    service.setRepeatMode(MusicPlaybackService.REPEAT_ALL);
    service.open(new long[] {10, 20, 30}, 0);
    service.play();
    assertEquals("Bluetooth and the lock screen see the whole queue", 3, playlistSize());

    long[] expected = {20, 30, 10, 20, 30, 10, 20, 30, 10};
    for (long id : expected) {
      TestPlayerRunHelper.advance(player)
          .untilPositionDiscontinuityWithReason(Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
      assertEquals(3, playlistSize());
      assertArrayEquals(new long[] {10, 20, 30}, service.getQueue());
      assertEquals(id, service.getAudioId());
    }
  }

  @Test
  public void enqueuedSongsGoNextLastOrPlayNow() {
    service.open(new long[] {1, 2, 3}, 0);

    service.enqueue(new long[] {7}, MusicPlaybackService.NEXT);
    service.enqueue(new long[] {8}, MusicPlaybackService.LAST);
    assertArrayEquals(new long[] {1, 7, 2, 3, 8}, service.getQueue());
    assertEquals(1, service.getAudioId());

    service.enqueue(new long[] {9}, 1 /* NOW */);
    assertArrayEquals(new long[] {1, 7, 2, 3, 8, 9}, service.getQueue());
    assertPlaying(9, 5);
    assertTrue(service.isPlaying());
  }

  @Test
  public void aSongQueuedNextWhilePausedIsTheNextOneToPlay() {
    service.open(new long[] {1, 2, 3}, 0);
    service.pause();

    service.enqueue(new long[] {9}, MusicPlaybackService.NEXT);
    service.gotoNext();

    assertEquals(9, service.getAudioId());
  }

  @Test
  public void songsQueuedToAnEmptyQueueLoadPaused() {
    service.enqueue(new long[] {4, 5}, MusicPlaybackService.LAST);

    assertArrayEquals(new long[] {4, 5}, service.getQueue());
    assertEquals(4, service.getAudioId());
    assertFalse(service.isPlaying());
    assertFalse(service.isStopped());
  }

  @Test
  public void movingASongKeepsThePlayingSongPlaying() {
    service.open(new long[] {1, 2, 3, 4}, 1);

    service.moveQueueItem(1, 3);

    assertArrayEquals(new long[] {1, 3, 4, 2}, service.getQueue());
    assertPlaying(2, 3);
  }

  @Test
  public void removingThePlayingSongPlaysTheFirstSongThatStaysAndTellsListenersOnce() {
    service.open(new long[] {1, 2, 3, 4}, 1);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    long queueChanges = broadcasts(MusicPlaybackService.QUEUE_CHANGED);
    long metaChanges = broadcasts(MusicPlaybackService.META_CHANGED);

    assertEquals(2, service.removeTracks(new long[] {2, 3}));
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));

    assertArrayEquals(new long[] {1, 4}, service.getQueue());
    assertPlaying(4, 1);
    assertTrue(service.isPlaying());
    assertEquals(1, broadcasts(MusicPlaybackService.QUEUE_CHANGED) - queueChanges);
    assertEquals(1, broadcasts(MusicPlaybackService.META_CHANGED) - metaChanges);
  }

  @Test
  public void removingOtherSongsKeepsThePlayingSong() {
    service.open(new long[] {1, 2, 3, 2}, 2);

    assertEquals(2, service.removeTrack(2));
    assertEquals(1, service.removeTracks(-5, 0));

    assertArrayEquals(new long[] {3}, service.getQueue());
    assertPlaying(3, 0);
  }

  @Test
  public void removingTheLastSongStopsPlayback() {
    service.open(new long[] {5}, 0);
    assertTrue(service.isPlaying());

    assertEquals(1, service.removeTrack(5));

    assertArrayEquals(new long[0], service.getQueue());
    assertEquals(-1, service.getAudioId());
    assertEquals(-1, service.getQueuePosition());
    assertFalse(service.isPlaying());
    assertTrue(service.isStopped());
  }

  @Test
  public void clearingTheQueueKeepsThePlayingSongPlaying() {
    service.open(new long[] {1, 2, 3}, 1);

    service.clearQueue();

    assertArrayEquals(new long[] {2}, service.getQueue());
    assertTrue(service.isPlaying());
  }

  @Test
  public void shuffleOnAndOffKeepsThePlayingSong() {
    service.open(new long[] {1, 2, 3, 4, 5}, 3);

    service.enableShuffle(true);
    assertTrue(service.isShuffleEnabled());
    assertTrue(player.getShuffleModeEnabled());
    assertPlaying(4, 3);

    service.enableShuffle(false);
    assertFalse(player.getShuffleModeEnabled());
    assertPlaying(4, 3);
  }

  @Test
  public void shuffleRepeatAndPauseFromAControllerShowInTheApp() {
    service.open(new long[] {1, 2, 3}, 0);
    assertTrue(service.isPlaying());

    player.setShuffleModeEnabled(true);
    player.setRepeatMode(Player.REPEAT_MODE_ONE);
    player.pause();

    assertTrue(service.isShuffleEnabled());
    assertEquals(MusicPlaybackService.REPEAT_CURRENT, service.getRepeatMode());
    assertFalse(service.isPlaying());
  }

  @Test
  public void playingAFinishedQueueStartsItOver() throws Exception {
    service.setRepeatMode(MusicPlaybackService.REPEAT_NONE);
    service.open(new long[] {1, 2}, 1);
    TestPlayerRunHelper.advance(player).untilState(Player.STATE_ENDED);
    assertFalse(service.isPlaying());

    service.play();

    assertPlaying(1, 0);
    assertTrue(service.isPlaying());
  }

  @Test
  public void removingFromAnotherThreadWaitsForThePlayerThread() throws Exception {
    service.open(new long[] {1, 2, 3, 4}, 0);
    AtomicInteger removed = new AtomicInteger(-1);
    AtomicReference<long[]> queueAfter = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(1);
    Thread loader =
        new Thread(
            () -> {
              removed.set(service.removeTracks(new long[] {2, 4}));
              queueAfter.set(service.getQueue());
              done.countDown();
            });
    loader.start();
    while (!done.await(10, TimeUnit.MILLISECONDS)) {
      ShadowLooper.idleMainLooper();
    }

    assertEquals(2, removed.get());
    assertArrayEquals("what it reads next shows the removal", new long[] {1, 3}, queueAfter.get());
  }

  @Test
  public void theMainThreadDoesNotWaitForThePlayerThread() throws Exception {
    usePlayerThread();
    onPlayerThread(() -> service.open(new long[] {1, 2, 3, 2}, 0));
    shadowOf(playerThread.getLooper()).pause();

    assertEquals("counted from the snapshot", 2, service.removeTrack(2));
    assertArrayEquals(
        "the player thread hasn't run it yet", new long[] {1, 2, 3, 2}, service.getQueue());
    shadowOf(playerThread.getLooper()).idle();

    assertArrayEquals(new long[] {1, 3}, service.getQueue());
  }

  @Test
  public void theSnapshotOtherThreadsReadMatchesThePlayerAfterEachChange() throws Exception {
    usePlayerThread();
    List<Runnable> changes =
        List.of(
            () -> service.open(new long[] {1, 2, 3, 4, 5}, 1),
            () -> service.enqueue(new long[] {6}, MusicPlaybackService.NEXT),
            () -> service.enqueue(new long[] {7}, MusicPlaybackService.LAST),
            () -> service.moveQueueItem(0, 5),
            () -> service.enableShuffle(true),
            service::gotoNext,
            service::gotoPrev,
            () -> service.setQueuePosition(4),
            () -> service.removeTrack(3),
            () -> service.removeTracks(0, 1),
            () -> service.enableShuffle(false),
            service::clearQueue);
    for (Runnable change : changes) {
      change.run(); // from the main thread: posted to the player thread
      shadowOf(playerThread.getLooper()).idle();
      AtomicReference<long[]> playlist = new AtomicReference<>();
      AtomicInteger index = new AtomicInteger();
      onPlayerThread(
          () -> {
            long[] ids = new long[player.getMediaItemCount()];
            for (int i = 0; i < ids.length; i++) {
              ids[i] = Long.parseLong(player.getMediaItemAt(i).mediaId);
            }
            playlist.set(ids);
            index.set(player.getCurrentMediaItemIndex());
          });

      assertArrayEquals(playlist.get(), service.getQueue());
      assertEquals(index.get(), service.getQueuePosition());
      assertEquals(playlist.get()[index.get()], service.getAudioId());
    }
  }

  @Test
  public void aSavedQueueIsReloadedAtTheSameSongAndPosition() throws Exception {
    DataStore<Preferences> store =
        DataStoreManager.dataStore(
            ApplicationProvider.getApplicationContext(), "queue-test-" + UUID.randomUUID());
    setField(service, "serviceDataStore", store);
    service.open(new long[] {10, 20, 3000000000L}, 2);
    TestPlayerRunHelper.advance(player).untilState(Player.STATE_READY);
    service.seek(4_000);
    invoke(service, "saveQueue", true);

    MusicPlaybackService restarted =
        Robolectric.buildService(MusicPlaybackService.class).create().get();
    ExoPlayer restartedPlayer = newPlayer();
    restarted.attachPlayer(restartedPlayer);
    setField(restarted, "serviceDataStore", store);
    invoke(restarted, "reloadQueue");

    assertArrayEquals(new long[] {10, 20, 3000000000L}, restarted.getQueue());
    assertEquals(2, restarted.getQueuePosition());
    assertEquals(3000000000L, restarted.getAudioId());
    assertEquals(4_000, restartedPlayer.getCurrentPosition());
    assertFalse("restored paused", restarted.isPlaying());
  }

  /**
   * Starting, the service set the repeat and shuffle preferences before reloading the queue, and
   * setting them saved the position of the still empty queue over the saved one: every restart
   * forgot the queue.
   */
  @Test
  public void theQueueSavedBeforeARestartIsBackWhenTheServiceStarts() throws Exception {
    DataStore<Preferences> store =
        DataStoreManager.dataStore(
            ApplicationProvider.getApplicationContext(), "queue-test-" + UUID.randomUUID());
    DataStoreManager.putString(store, "queue", MusicPlaybackService.encodeQueue(new long[] {7, 8}));
    DataStoreManager.putInt(store, "curpos", 1);
    DataStoreManager.putLong(store, "seekpos", 0);
    setField(service, "serviceDataStore", store);
    setField(service, "mCardId", -1);
    DataStoreManager.putInt(store, "cardid", -1);

    // What initService() does with the saved queue and the preferences.
    invoke(service, "restoreQueue");

    assertArrayEquals(new long[] {7, 8}, service.getQueue());
    assertEquals(1, service.getQueuePosition());
    assertEquals(8, service.getAudioId());
    assertEquals("saved again where it is", 1, DataStoreManager.getInt(store, "curpos", -9));
  }

  /** The receiver runs on the main thread; ExoPlayer, and so the queue, only on the player's. */
  @Test
  public void aMountedCardReloadsTheSavedQueueOnThePlayerThread() throws Exception {
    usePlayerThread();
    DataStore<Preferences> store =
        DataStoreManager.dataStore(
            ApplicationProvider.getApplicationContext(), "queue-test-" + UUID.randomUUID());
    DataStoreManager.putString(store, "queue", MusicPlaybackService.encodeQueue(new long[] {4, 5}));
    DataStoreManager.putInt(store, "curpos", 0);
    DataStoreManager.putInt(store, "cardid", -1);
    setField(service, "serviceDataStore", store);
    setField(service, "mCardId", -1);
    invoke(service, "registerExternalStorageListener");

    service.sendBroadcast(
        new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file:///storage/sdcard")));
    ShadowLooper.idleMainLooper();
    shadowOf(playerThread.getLooper()).idle();

    assertArrayEquals(new long[] {4, 5}, service.getQueue());
  }

  @Test
  public void theSavedQueueKeepsTheFormatEarlierVersionsWrote() {
    // Written by Apollo's own queue before #1452: hex digits, lowest first, ';' after each id.
    String saved = "a;ff;0001;0;";

    assertArrayEquals(new long[] {10, 255, 4096, 0}, MusicPlaybackService.decodeQueue(saved));
    assertEquals(saved, MusicPlaybackService.encodeQueue(new long[] {10, 255, 4096, 0}));
    assertArrayEquals(new long[0], MusicPlaybackService.decodeQueue("a;zz;"));
    assertArrayEquals(new long[0], MusicPlaybackService.decodeQueue(""));
    assertArrayEquals(new long[0], MusicPlaybackService.decodeQueue(null));
    assertArrayEquals(
        "a file MediaStore didn't know, saved as -1",
        new long[0],
        MusicPlaybackService.decodeQueue("a;ffffffffffffffff;"));
    assertEquals(
        "a file MediaStore doesn't know", "", MusicPlaybackService.encodeQueue(new long[] {5, -1}));
  }

  private void assertPlaying(long audioId, int position) {
    assertEquals(audioId, service.getAudioId());
    assertEquals(position, service.getQueuePosition());
    assertEquals(audioId, Long.parseLong(player.getCurrentMediaItem().mediaId));
  }

  private int playlistSize() {
    return player.getMediaItemCount();
  }

  private ExoPlayer newPlayer() {
    ExoPlayer p =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(new FakeMediaSourceFactory())
            .build();
    players.add(p);
    return p;
  }

  /** Moves the service's player thread off the main thread, as in the app. */
  private void usePlayerThread() throws Exception {
    player.release();
    players.clear();
    playerThread = new HandlerThread("test-player");
    playerThread.start();
    setPlayerHandler(new MusicPlaybackService.MusicPlayerHandler(playerThread.getLooper()));
    Context context = ApplicationProvider.getApplicationContext();
    onPlayerThread(
        () -> {
          player =
              new TestExoPlayerBuilder(context)
                  .setMediaSourceFactory(new FakeMediaSourceFactory())
                  .setLooper(playerThread.getLooper())
                  .build();
          players.add(player);
          service.attachPlayer(player);
        });
    // Runs only when a test idles it, so nothing changes between a change and its checks.
    shadowOf(playerThread.getLooper()).pause();
  }

  private void onPlayerThread(Runnable task) {
    new Handler(playerThread.getLooper()).post(task);
    shadowOf(playerThread.getLooper()).idle();
  }

  private static long broadcasts(String action) {
    Application app = ApplicationProvider.getApplicationContext();
    return shadowOf(app).getBroadcastIntents().stream()
        .map(Intent::getAction)
        .filter(action::equals)
        .count();
  }

  private static void setPlayerHandler(Handler handler) throws Exception {
    Field field = MusicPlaybackService.class.getDeclaredField("mPlayerHandler");
    field.setAccessible(true);
    field.set(null, handler);
  }

  private static void setConfigurationManager(ConfigurationManager instance) throws Exception {
    Field field = ConfigurationManager.class.getDeclaredField("instance");
    field.setAccessible(true);
    field.set(null, instance);
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = MusicPlaybackService.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void invoke(Object target, String name, Object... args) throws Exception {
    for (Method method : MusicPlaybackService.class.getDeclaredMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == args.length) {
        method.setAccessible(true);
        method.invoke(target, args);
        return;
      }
    }
    throw new NoSuchMethodException(name);
  }
}
