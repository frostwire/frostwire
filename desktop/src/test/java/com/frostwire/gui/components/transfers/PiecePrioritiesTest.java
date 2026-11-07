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

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Priority;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Pieces tab's priority actions: libtorrent off the EDT, the menu back on it. */
class PiecePrioritiesTest {

  private final ExecutorService background = Executors.newSingleThreadExecutor();
  private final PiecePriorities priorities =
      new PiecePriorities(background, SwingUtilities::invokeLater);

  @AfterEach
  void shutDown() {
    background.shutdownNow();
  }

  @Test
  void choosingAPrioritySetsItOffTheEdt() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    CountDownLatch changed = new CountDownLatch(1);
    AtomicReference<Thread> refreshThread = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () ->
            priorities.apply(
                handle,
                3,
                Priority.SEVEN,
                () -> {
                  refreshThread.set(Thread.currentThread());
                  changed.countDown();
                }));
    assertTrue(changed.await(5, TimeUnit.SECONDS), "the priority was never set");

    assertEquals(Priority.SEVEN, handle.priorities[3]);
    assertFalse(handle.calls.isEmpty());
    for (Call call : handle.calls) {
      assertFalse(call.onEdt, call.name + " ran on the EDT");
    }
    assertFalse(isEdt(refreshThread.get()), "the refresh after a change ran on the EDT");
  }

  @Test
  void aPieceDownloadedMeanwhileKeepsItsPriority() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    handle.have[4] = true;
    CountDownLatch done = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(
        () -> priorities.apply(handle, 4, Priority.IGNORE, done::countDown));
    background.submit(() -> {}).get(5, TimeUnit.SECONDS);

    assertEquals(1, done.getCount(), "nothing changed, nothing to refresh");
    assertEquals(Priority.FOUR, handle.priorities[4]);
    assertTrue(handle.calls.stream().noneMatch(c -> c.name.equals("set")));
  }

  @Test
  void lookingAPieceUpReadsOffTheEdtAndAnswersOnIt() throws Exception {
    FakeHandle handle = new FakeHandle(10);
    handle.have[2] = true;
    handle.priorities[5] = Priority.IGNORE;
    CountDownLatch answered = new CountDownLatch(2);
    List<PiecePriorities.PieceState> states = new CopyOnWriteArrayList<>();
    List<Boolean> answeredOnEdt = new CopyOnWriteArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          for (int piece : new int[] {2, 5}) {
            priorities.lookup(
                handle,
                piece,
                state -> {
                  states.add(state);
                  answeredOnEdt.add(SwingUtilities.isEventDispatchThread());
                  answered.countDown();
                });
          }
        });
    assertTrue(answered.await(5, TimeUnit.SECONDS));

    assertEquals(new PiecePriorities.PieceState(2, true, Priority.FOUR), states.get(0));
    assertEquals(new PiecePriorities.PieceState(5, false, Priority.IGNORE), states.get(1));
    assertEquals(List.of(true, true), answeredOnEdt);
    for (Call call : handle.calls) {
      assertFalse(call.onEdt, call.name + " ran on the EDT");
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
    SwingUtilities.invokeAndWait(() -> {});

    assertTrue(states.isEmpty());
    assertEquals(Priority.FOUR, handle.priorities[1]);
  }

  @Test
  void menuOffersEveryPriorityWithTheCurrentOneSelected() throws Exception {
    List<Priority> chosen = new ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          JPopupMenu menu =
              PiecePriorities.createMenu(
                  new PiecePriorities.PieceState(7, false, Priority.FOUR), chosen::add);
          List<JRadioButtonMenuItem> items = priorityItems(menu);
          assertEquals(PiecePriorities.CHOICES.length, items.size());
          for (int i = 0; i < items.size(); i++) {
            JRadioButtonMenuItem item = items.get(i);
            assertTrue(item.isEnabled());
            assertEquals(PiecePriorities.CHOICES[i] == Priority.FOUR, item.isSelected());
          }
          items.get(0).doClick();
          items.get(3).doClick();
        });
    assertEquals(List.of(Priority.IGNORE, Priority.SEVEN), chosen);
  }

  @Test
  void aDownloadedPieceOffersNoPriorityToChange() throws Exception {
    List<Priority> chosen = new ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          JPopupMenu menu =
              PiecePriorities.createMenu(
                  new PiecePriorities.PieceState(7, true, Priority.FOUR), chosen::add);
          List<JRadioButtonMenuItem> items = priorityItems(menu);
          assertEquals(PiecePriorities.CHOICES.length, items.size());
          for (JRadioButtonMenuItem item : items) {
            assertFalse(item.isEnabled(), item.getText() + " is enabled on a downloaded piece");
            item.doClick();
          }
        });
    assertTrue(chosen.isEmpty());
  }

  @Test
  void labelsFollowLibtorrentsLevels() {
    // 4 is libtorrent's default piece priority, 1 (jlibtorrent's NORMAL) its lowest
    assertEquals(PiecePriorities.label(Priority.FOUR), PiecePriorities.describe(Priority.FOUR));
    assertEquals("Normal", PiecePriorities.label(Priority.FOUR));
    assertEquals("Low", PiecePriorities.label(Priority.NORMAL));
    assertEquals("High", PiecePriorities.label(Priority.SEVEN));
    assertEquals("Don't Download", PiecePriorities.label(Priority.IGNORE));
    assertEquals("High (5)", PiecePriorities.describe(Priority.FIVE));
    assertEquals("Low (2)", PiecePriorities.describe(Priority.TWO));

    assertEquals(PiecePriorities.MARK_SKIP, PiecePriorities.mark(0));
    // 1 is what FrostWire gives the files it downloads: not tinted, like libtorrent's default 4
    assertEquals(PiecePriorities.MARK_NONE, PiecePriorities.mark(1));
    assertEquals(PiecePriorities.MARK_NONE, PiecePriorities.mark(3));
    assertEquals(PiecePriorities.MARK_NONE, PiecePriorities.mark(4));
    assertEquals(PiecePriorities.MARK_HIGH, PiecePriorities.mark(5));
    assertEquals(PiecePriorities.MARK_HIGH, PiecePriorities.mark(7));
  }

  private static List<JRadioButtonMenuItem> priorityItems(JPopupMenu menu) {
    List<JRadioButtonMenuItem> items = new ArrayList<>();
    JMenuItem title = null;
    for (Component component : menu.getComponents()) {
      if (component instanceof JRadioButtonMenuItem) {
        items.add((JRadioButtonMenuItem) component);
      } else if (component instanceof JMenuItem && title == null) {
        title = (JMenuItem) component;
      }
    }
    assertNotNull(title, "the menu names the piece");
    assertFalse(title.isEnabled());
    return items;
  }

  private static boolean isEdt(Thread thread) {
    assertNotNull(thread);
    return thread.getName().startsWith("AWT-EventQueue");
  }

  private record Call(String name, boolean onEdt) {}

  /** Records which thread each libtorrent call ran on. */
  private static final class FakeHandle implements PiecePriorities.PieceHandle {
    final boolean[] have;
    final Priority[] priorities;
    final List<Call> calls = new CopyOnWriteArrayList<>();
    volatile boolean valid = true;

    FakeHandle(int pieces) {
      have = new boolean[pieces];
      priorities = new Priority[pieces];
      java.util.Arrays.fill(priorities, Priority.FOUR);
    }

    private void record(String name) {
      calls.add(new Call(name, SwingUtilities.isEventDispatchThread()));
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

  @Test
  void noCallIsMadeBeforeTheBackgroundRuns() throws Exception {
    // the EDT only queues the work: a stalled background queue leaves the handle untouched
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
    SwingUtilities.invokeAndWait(
        () -> {
          priorities.apply(handle, 0, Priority.IGNORE, null);
          priorities.lookup(handle, 0, state -> {});
        });
    assertTrue(handle.calls.isEmpty(), "libtorrent was called on the EDT: " + handle.calls);
    release.countDown();
    background.submit(() -> {}).get(5, TimeUnit.SECONDS);
    assertEquals(Priority.IGNORE, handle.priorities[0]);
  }
}
