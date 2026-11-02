/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.net.Uri;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ShuffleOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The play queue, kept as ExoPlayer's playlist: one MediaItem per song, whose media id is the
 * song's MediaStore audio id. ExoPlayer walks it, shuffles it and repeats it, so the app,
 * Bluetooth, the lock screen, Android Auto and Wear all see the same queue and move through it the
 * same way (GitHub #1452). The playlist holds exactly the queue, so it never grows while songs
 * play.
 *
 * <p>Threading: only the player's application thread may call the methods that change the queue, as
 * for ExoPlayer itself. After every change that thread publishes an immutable {@link Snapshot},
 * which {@link #snapshot()} returns to any thread without locking: one writer, immutable reads.
 */
final class PlaybackQueue {

  /** Told on the player thread, once per change, after the snapshot shows it. */
  interface Listener {
    /** Songs were added, removed or moved. */
    void onQueueChanged();

    /** Another song started or became current, or the queue emptied ({@code audioId} -1). */
    void onSongChanged(long audioId);
  }

  /** What the queue holds, as of the last change. Immutable, so any thread may read it. */
  static final class Snapshot {
    static final Snapshot EMPTY = new Snapshot(new long[0], C.INDEX_UNSET);

    private final long[] ids;
    private final int index;

    private Snapshot(long[] ids, int index) {
      this.ids = ids;
      this.index = index;
    }

    /** The queue's audio ids, in queue order (a copy). */
    long[] ids() {
      return ids.clone();
    }

    int size() {
      return ids.length;
    }

    /** The current song's position in the queue, or -1 when the queue is empty. */
    int index() {
      return index;
    }

    /** The current song's audio id, or -1. */
    long audioId() {
      return index >= 0 && index < ids.length ? ids[index] : -1;
    }

    /** How many queue entries are one of {@code wanted}. */
    int count(long[] wanted) {
      return marked(ids, wanted).cardinality();
    }

    /** How many entries {@code first..last}, clamped to the queue, cover. */
    int countRange(int first, int last) {
      return Math.max(0, Math.min(last, ids.length - 1) - Math.max(first, 0) + 1);
    }
  }

  private final ExoPlayer player;
  private final Listener listener;
  private final Random random = new Random();
  private volatile Snapshot snapshot = Snapshot.EMPTY;

  // Player thread only.
  private long currentSongId = -1;
  private int changing;
  private boolean songChangedWhileChanging;

  PlaybackQueue(ExoPlayer player, Listener listener) {
    this.player = player;
    this.listener = listener;
    player.addListener(
        new Player.Listener() {
          @Override
          public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
            final Snapshot before = snapshot;
            publish();
            if (changing == 0 && !Arrays.equals(before.ids, snapshot.ids)) {
              listener.onQueueChanged();
            }
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            publish();
            final long id = audioIdOf(mediaItem);
            final boolean newSong =
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                    || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                    || (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                        && id != currentSongId);
            currentSongId = id;
            if (!newSong) {
              return;
            }
            if (changing > 0) {
              songChangedWhileChanging = true;
            } else {
              listener.onSongChanged(id);
            }
          }
        });
    publish();
    currentSongId = snapshot.audioId();
  }

  /** The queue as of the last change; any thread. */
  Snapshot snapshot() {
    return snapshot;
  }

  /**
   * Makes {@code ids} the queue and {@code start} the current song, at {@code positionMs}; with a
   * negative {@code start}, the first song in playback order. When {@code ids} already is the
   * queue, only moves to {@code start}.
   */
  void open(long[] ids, int start, long positionMs) {
    change(
        () -> {
          if (ids.length == 0) {
            player.clearMediaItems();
            return null;
          }
          final int index = Math.min(start, ids.length - 1);
          if (Arrays.equals(ids, snapshot.ids)) {
            player.seekTo(index >= 0 ? index : firstInPlaybackOrder(), positionMs);
          } else if (index >= 0) {
            player.setMediaItems(mediaItemsOf(ids), index, positionMs);
          } else {
            player.setMediaItems(mediaItemsOf(ids), /* resetPosition= */ true);
          }
          if (player.getShuffleModeEnabled()) {
            shuffleFromCurrent();
          }
          prepareIfIdle();
          return null;
        });
  }

  /** Makes a file MediaStore doesn't know the whole queue; its audio id is -1. */
  void openUri(Uri uri) {
    change(
        () -> {
          player.setMediaItem(MediaItem.fromUri(uri));
          prepareIfIdle();
          return null;
        });
  }

  /**
   * Adds {@code ids} right after the current song ({@code next}) or at the end. With shuffle on,
   * songs added next play next, and songs added at the end play after the current one, in random
   * order.
   *
   * @return the queue position of the first song added, or -1
   */
  int add(long[] ids, boolean next) {
    return change(
        () -> {
          if (ids.length == 0) {
            return -1;
          }
          final int size = player.getMediaItemCount();
          final int at = next && size > 0 ? player.getCurrentMediaItemIndex() + 1 : size;
          player.addMediaItems(at, mediaItemsOf(ids));
          if (size > 0 && player.getShuffleModeEnabled()) {
            playAddedAfterCurrent(at, ids.length, next);
          }
          prepareIfIdle();
          return at;
        });
  }

  /** Moves the song at {@code from} to {@code to}; both are clamped to the queue. */
  void move(int from, int to) {
    change(
        () -> {
          final int last = player.getMediaItemCount() - 1;
          final int source = Math.max(0, Math.min(from, last));
          final int target = Math.max(0, Math.min(to, last));
          if (last > 0 && source != target) {
            player.moveMediaItem(source, target);
          }
          return null;
        });
  }

  /**
   * Removes every entry of {@code ids} at once. If the current song is one of them, playback moves
   * straight to the first song after it that stays, in playback order.
   *
   * @return how many entries were removed
   */
  int remove(long[] ids) {
    return change(() -> removeMarked(marked(snapshot.ids, ids)));
  }

  /** Removes the entries {@code first..last}, clamped to the queue, as {@link #remove}. */
  int removeRange(int first, int last) {
    return change(
        () -> {
          final BitSet doomed = new BitSet();
          final int end = Math.min(last, player.getMediaItemCount() - 1);
          if (end >= Math.max(first, 0)) {
            doomed.set(Math.max(first, 0), end + 1);
          }
          return removeMarked(doomed);
        });
  }

  /**
   * Removes every entry but the current song.
   *
   * @return how many entries were removed
   */
  int removeAllButCurrent() {
    return change(
        () -> {
          final int size = player.getMediaItemCount();
          if (size == 0) {
            return 0;
          }
          final BitSet doomed = new BitSet();
          doomed.set(0, size);
          doomed.clear(player.getCurrentMediaItemIndex());
          return removeMarked(doomed);
        });
  }

  /** Makes the song at {@code index} current, from its start. */
  void seekTo(int index) {
    change(
        () -> {
          if (index >= 0 && index < player.getMediaItemCount()) {
            player.seekToDefaultPosition(index);
          }
          return null;
        });
  }

  /** Goes to the next song, as a controller's "next" does; nothing at the end without repeat. */
  void next() {
    change(
        () -> {
          player.seekToNext();
          return null;
        });
  }

  /**
   * Goes to the previous song, as a controller's "previous" does: the current song restarts when it
   * played past ExoPlayer's max-seek-to-previous position.
   */
  void previous() {
    change(
        () -> {
          player.seekToPrevious();
          return null;
        });
  }

  /** The first song again, when the queue ended: what playing a finished queue starts with. */
  void restartIfEnded() {
    if (player.getPlaybackState() == Player.STATE_ENDED && player.getMediaItemCount() > 0) {
      change(
          () -> {
            player.seekToDefaultPosition(firstInPlaybackOrder());
            return null;
          });
    }
  }

  /** Turning shuffle on keeps the current song and plays every other song after it. */
  void setShuffle(boolean on) {
    if (on == player.getShuffleModeEnabled()) {
      return;
    }
    if (on) {
      shuffleFromCurrent();
    }
    player.setShuffleModeEnabled(on);
  }

  void setRepeatMode(int apolloRepeatMode) {
    player.setRepeatMode(toPlayerRepeatMode(apolloRepeatMode));
  }

  static int toPlayerRepeatMode(int apolloRepeatMode) {
    switch (apolloRepeatMode) {
      case MusicPlaybackService.REPEAT_CURRENT:
        return Player.REPEAT_MODE_ONE;
      case MusicPlaybackService.REPEAT_ALL:
        return Player.REPEAT_MODE_ALL;
      default:
        return Player.REPEAT_MODE_OFF;
    }
  }

  static int toApolloRepeatMode(int playerRepeatMode) {
    switch (playerRepeatMode) {
      case Player.REPEAT_MODE_ONE:
        return MusicPlaybackService.REPEAT_CURRENT;
      case Player.REPEAT_MODE_ALL:
        return MusicPlaybackService.REPEAT_ALL;
      default:
        return MusicPlaybackService.REPEAT_NONE;
    }
  }

  /** The MediaItem for a MediaStore song: its audio id as media id, its content URI. */
  static MediaItem mediaItemOf(long audioId) {
    return new MediaItem.Builder()
        .setMediaId(Long.toString(audioId))
        .setUri(
            MusicPlaybackService.MultiPlayer.resolveUri(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + audioId))
        .build();
  }

  /** The audio id a MediaItem stands for, or -1 when it isn't a MediaStore song. */
  static long audioIdOf(@Nullable MediaItem mediaItem) {
    if (mediaItem == null) {
      return -1;
    }
    try {
      return Long.parseLong(mediaItem.mediaId);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** Runs one queue operation; listeners hear of it once, after it, from the published snapshot. */
  private <T> T change(Supplier<T> operation) {
    final Snapshot before = snapshot;
    final T result;
    changing++;
    try {
      result = operation.get();
    } finally {
      changing--;
    }
    publish();
    if (changing == 0) {
      if (!Arrays.equals(before.ids, snapshot.ids)) {
        listener.onQueueChanged();
      }
      if (songChangedWhileChanging) {
        songChangedWhileChanging = false;
        listener.onSongChanged(currentSongId);
      }
    }
    return result;
  }

  private void publish() {
    final int size = player.getMediaItemCount();
    final long[] ids = new long[size];
    for (int i = 0; i < size; i++) {
      ids[i] = audioIdOf(player.getMediaItemAt(i));
    }
    snapshot = new Snapshot(ids, size == 0 ? C.INDEX_UNSET : player.getCurrentMediaItemIndex());
  }

  private int removeMarked(BitSet doomed) {
    final int removed = doomed.cardinality();
    if (removed == 0) {
      return 0;
    }
    if (removed >= player.getMediaItemCount()) {
      player.clearMediaItems();
      return removed;
    }
    final int current = player.getCurrentMediaItemIndex();
    if (doomed.get(current)) {
      player.seekToDefaultPosition(nextSurvivor(current, doomed));
    }
    // Highest range first, so the ranges below keep their positions.
    int end = doomed.length() - 1;
    while (end >= 0) {
      final int start = doomed.previousClearBit(end) + 1;
      player.removeMediaItems(start, end + 1);
      end = doomed.previousSetBit(start - 1);
    }
    return removed;
  }

  /** The first song after {@code index} in playback order, wrapping, that isn't doomed. */
  private int nextSurvivor(int index, BitSet doomed) {
    final Timeline timeline = player.getCurrentTimeline();
    final boolean shuffle = player.getShuffleModeEnabled();
    int i = index;
    do {
      i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_ALL, shuffle);
    } while (doomed.get(i));
    return i;
  }

  private int firstInPlaybackOrder() {
    return player.getCurrentTimeline().getFirstWindowIndex(player.getShuffleModeEnabled());
  }

  private void prepareIfIdle() {
    if (player.getPlaybackState() == Player.STATE_IDLE && player.getMediaItemCount() > 0) {
      player.prepare();
    }
  }

  /** A new shuffle order that starts with the current song, so every other song plays after it. */
  private void shuffleFromCurrent() {
    final int size = player.getMediaItemCount();
    if (size < 2) {
      return;
    }
    final int current = player.getCurrentMediaItemIndex();
    final int[] order = new int[size];
    order[0] = current;
    for (int i = 0, j = 1; i < size; i++) {
      if (i != current) {
        order[j++] = i;
      }
    }
    for (int i = size - 1; i > 1; i--) {
      final int j = 1 + random.nextInt(i);
      final int swap = order[i];
      order[i] = order[j];
      order[j] = swap;
    }
    player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(order, random.nextLong()));
  }

  /**
   * ExoPlayer shuffles added songs in anywhere, also before the current song, where they would not
   * play until the queue repeats. Puts them after it: in order when added to play next, else at
   * random places.
   */
  private void playAddedAfterCurrent(int first, int count, boolean next) {
    final Timeline timeline = player.getCurrentTimeline();
    final List<Integer> order = new ArrayList<>();
    for (int i = timeline.getFirstWindowIndex(true);
        i != C.INDEX_UNSET;
        i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)) {
      if (i < first || i >= first + count) {
        order.add(i);
      }
    }
    final int afterCurrent = order.indexOf(player.getCurrentMediaItemIndex()) + 1;
    for (int k = 0; k < count; k++) {
      final int at =
          next ? afterCurrent + k : afterCurrent + random.nextInt(order.size() - afterCurrent + 1);
      order.add(at, first + k);
    }
    final int[] shuffled = new int[order.size()];
    for (int i = 0; i < shuffled.length; i++) {
      shuffled[i] = order.get(i);
    }
    player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(shuffled, random.nextLong()));
  }

  private static List<MediaItem> mediaItemsOf(long[] ids) {
    final List<MediaItem> items = new ArrayList<>(ids.length);
    for (long id : ids) {
      items.add(mediaItemOf(id));
    }
    return items;
  }

  /** The positions in {@code queue} holding one of {@code wanted}. */
  private static BitSet marked(long[] queue, long[] wanted) {
    final BitSet positions = new BitSet();
    if (wanted == null || wanted.length == 0) {
      return positions;
    }
    final long[] sorted = wanted.clone();
    Arrays.sort(sorted);
    for (int i = 0; i < queue.length; i++) {
      if (Arrays.binarySearch(sorted, queue[i]) >= 0) {
        positions.set(i);
      }
    }
    return positions;
  }
}
