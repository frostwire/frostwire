/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Priority;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A piece's priority from the Pieces view: libtorrent on the background executor, the answer back
 * on the UI one. A single thread executor named "ui" stands in for the EDT or Android's main
 * thread.
 */
class PiecePrioritiesTest {
  private static final String UI_THREAD = "piece-priorities-test-ui";

  private final ExecutorService background = Executors.newSingleThreadExecutor();
  private final ExecutorService ui =
      Executors.newSingleThreadExecutor(r -> new Thread(r, UI_THREAD));
  private final PiecePriorities priorities = new PiecePriorities(background, ui);

  @AfterEach
  void shutDown() {
    background.shutdownNow();
    ui.shutdownNow();
  }

  @Test
  void choosingAPrioritySetsItOffTheUiThread() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    CountDownLatch changed = new CountDownLatch(1);
    AtomicReference<Thread> refreshThread = new AtomicReference<>();
    ui.submit(
            () ->
                priorities.apply(
                    handle,
                    3,
                    Priority.SEVEN,
                    () -> {
                      refreshThread.set(Thread.currentThread());
                      changed.countDown();
                    }))
        .get(5, TimeUnit.SECONDS);
    assertTrue(changed.await(5, TimeUnit.SECONDS), "the priority was never set");

    assertEquals(Priority.SEVEN, handle.priorities[3]);
    assertFalse(handle.calls.isEmpty());
    for (Call call : handle.calls) {
      assertFalse(call.onUi, call.name + " ran on the UI thread");
    }
    assertFalse(isUi(refreshThread.get()), "the refresh after a change ran on the UI thread");
  }

  @Test
  void aPieceDownloadedMeanwhileKeepsItsPriority() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    handle.have[4] = true;
    CountDownLatch done = new CountDownLatch(1);
    priorities.apply(handle, 4, Priority.IGNORE, done::countDown);
    background.submit(() -> {}).get(5, TimeUnit.SECONDS);

    assertEquals(1, done.getCount(), "nothing changed, nothing to refresh");
    assertEquals(Priority.FOUR, handle.priorities[4]);
    assertTrue(handle.calls.stream().noneMatch(c -> c.name.equals("set")));
  }

  @Test
  void lookingAPieceUpReadsOffTheUiThreadAndAnswersOnIt() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    handle.have[2] = true;
    handle.priorities[5] = Priority.IGNORE;
    CountDownLatch answered = new CountDownLatch(2);
    List<PiecePriorities.PieceState> states = new CopyOnWriteArrayList<>();
    List<Boolean> answeredOnUi = new CopyOnWriteArrayList<>();
    ui.submit(
            () -> {
              for (int piece : new int[] {2, 5}) {
                priorities.lookup(
                    handle,
                    piece,
                    state -> {
                      states.add(state);
                      answeredOnUi.add(isUi(Thread.currentThread()));
                      answered.countDown();
                    });
              }
            })
        .get(5, TimeUnit.SECONDS);
    assertTrue(answered.await(5, TimeUnit.SECONDS));

    assertEquals(new PiecePriorities.PieceState(2, true, Priority.FOUR), states.get(0));
    assertEquals(new PiecePriorities.PieceState(5, false, Priority.IGNORE), states.get(1));
    assertEquals(List.of(true, true), answeredOnUi);
    for (Call call : handle.calls) {
      assertFalse(call.onUi, call.name + " ran on the UI thread");
    }
  }

  @Test
  void aGoneHandleAnswersNothing() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    handle.valid = false;
    List<PiecePriorities.PieceState> states = new ArrayList<>();
    priorities.lookup(handle, 1, states::add);
    priorities.apply(handle, 1, Priority.IGNORE, null);
    background.submit(() -> {}).get(5, TimeUnit.SECONDS);
    ui.submit(() -> {}).get(5, TimeUnit.SECONDS);

    assertTrue(states.isEmpty());
    assertEquals(Priority.FOUR, handle.priorities[1]);
  }

  @Test
  void noCallIsMadeBeforeTheBackgroundRuns() throws Exception {
    // the UI thread only queues the work: a stalled background queue leaves the handle untouched
    CountDownLatch release = new CountDownLatch(1);
    background.execute(
        () -> {
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
        });
    FakeHandle handle = new FakeHandle(4);
    ui.submit(
            () -> {
              priorities.apply(handle, 0, Priority.IGNORE, null);
              priorities.lookup(handle, 0, state -> {});
            })
        .get(5, TimeUnit.SECONDS);
    assertTrue(handle.calls.isEmpty(), "libtorrent was called on the UI thread: " + handle.calls);
    release.countDown();
    background.submit(() -> {}).get(5, TimeUnit.SECONDS);
    assertEquals(Priority.IGNORE, handle.priorities[0]);
  }

  @Test
  void offersDontDownloadLowNormalAndHigh() {
    // 4 is libtorrent's default piece priority, 1 (jlibtorrent's NORMAL) its lowest
    Priority[] choices = PiecePriorities.choices();
    assertArrayEquals(
        new Priority[] {Priority.IGNORE, Priority.NORMAL, Priority.FOUR, Priority.SEVEN}, choices);
    choices[0] = Priority.SEVEN;
    assertNotSame(choices, PiecePriorities.choices());
    assertEquals(Priority.IGNORE, PiecePriorities.choices()[0], "the choices can't be changed");
  }

  @Test
  void labelsFollowLibtorrentsLevels() {
    assertEquals(PiecePriorities.Label.DONT_DOWNLOAD, PiecePriorities.label(Priority.IGNORE));
    assertEquals(PiecePriorities.Label.LOW, PiecePriorities.label(Priority.NORMAL));
    assertEquals(PiecePriorities.Label.LOW, PiecePriorities.label(Priority.TWO));
    assertEquals(PiecePriorities.Label.NORMAL, PiecePriorities.label(Priority.FOUR));
    assertEquals(PiecePriorities.Label.HIGH, PiecePriorities.label(Priority.FIVE));
    assertEquals(PiecePriorities.Label.HIGH, PiecePriorities.label(Priority.SEVEN));
    assertEquals(PiecePriorities.Label.UNKNOWN, PiecePriorities.label(null));

    for (Priority choice : PiecePriorities.choices()) {
      assertTrue(PiecePriorities.isChoice(choice), choice + " is offered");
    }
    assertFalse(PiecePriorities.isChoice(Priority.TWO));
    assertFalse(PiecePriorities.isChoice(Priority.FIVE));
    assertFalse(PiecePriorities.isChoice(null));
  }

  @Test
  void anInBetweenPriorityIsShownAsTheNearestChoice() {
    List<Priority> choices = Arrays.asList(PiecePriorities.choices());
    assertEquals(choices.indexOf(Priority.IGNORE), PiecePriorities.choiceIndex(Priority.IGNORE));
    assertEquals(choices.indexOf(Priority.NORMAL), PiecePriorities.choiceIndex(Priority.TWO));
    assertEquals(choices.indexOf(Priority.NORMAL), PiecePriorities.choiceIndex(Priority.THREE));
    assertEquals(choices.indexOf(Priority.FOUR), PiecePriorities.choiceIndex(Priority.FOUR));
    assertEquals(choices.indexOf(Priority.SEVEN), PiecePriorities.choiceIndex(Priority.FIVE));
    assertEquals(choices.indexOf(Priority.SEVEN), PiecePriorities.choiceIndex(Priority.SEVEN));
    assertEquals(-1, PiecePriorities.choiceIndex(null));
  }

  @Test
  void onlyDontDownloadAndHighAreMarkedOnTheHive() {
    assertEquals(PiecePriorities.MARK_SKIP, PiecePriorities.mark(0));
    // 1 is what FrostWire gives the files it downloads: not tinted, like libtorrent's default 4
    assertEquals(PiecePriorities.MARK_NONE, PiecePriorities.mark(1));
    assertEquals(PiecePriorities.MARK_NONE, PiecePriorities.mark(3));
    assertEquals(PiecePriorities.MARK_NONE, PiecePriorities.mark(4));
    assertEquals(PiecePriorities.MARK_HIGH, PiecePriorities.mark(5));
    assertEquals(PiecePriorities.MARK_HIGH, PiecePriorities.mark(7));
  }

  private static boolean isUi(Thread thread) {
    return thread != null && UI_THREAD.equals(thread.getName());
  }

  private record Call(String name, boolean onUi) {}

  /** Records which thread each libtorrent call ran on. */
  private static final class FakeHandle implements PiecePriorities.PieceHandle {
    final boolean[] have;
    final Priority[] priorities;
    final List<Call> calls = new CopyOnWriteArrayList<>();
    volatile boolean valid = true;

    FakeHandle(int pieces) {
      have = new boolean[pieces];
      priorities = new Priority[pieces];
      Arrays.fill(priorities, Priority.FOUR);
    }

    private void record(String name) {
      calls.add(new Call(name, isUi(Thread.currentThread())));
    }

    @Override
    public boolean isValid() {
      record("isValid");
      return valid;
    }

    @Override
    public boolean havePiece(int piece) {
      record("havePiece");
      return have[piece];
    }

    @Override
    public Priority piecePriority(int piece) {
      record("get");
      return priorities[piece];
    }

    @Override
    public void piecePriority(int piece, Priority priority) {
      record("set");
      priorities[piece] = priority;
    }
  }
}
