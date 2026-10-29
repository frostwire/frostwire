/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * The first-visibility association check reads and writes the Windows registry; it froze the main
 * window as it first appeared when it ran on the EDT (frostwire/frostwire#1378).
 */
class ShellAssociationManagerThreadingTest {

  /** A shell association that records the thread of every registry call. */
  private static final class RecordingAssociation implements ShellAssociation {
    final List<Thread> callers = new CopyOnWriteArrayList<>();
    final List<String> calls = new CopyOnWriteArrayList<>();
    private final boolean available;
    private boolean registered;

    RecordingAssociation(boolean available) {
      this.available = available;
    }

    @Override
    public boolean isRegistered() {
      callers.add(Thread.currentThread());
      return registered;
    }

    @Override
    public boolean isAvailable() {
      callers.add(Thread.currentThread());
      return available;
    }

    @Override
    public void register() {
      callers.add(Thread.currentThread());
      calls.add("register");
      registered = true;
    }

    @Override
    public void unregister() {
      callers.add(Thread.currentThread());
      calls.add("unregister");
      registered = false;
    }
  }

  @Test
  void theRegistryWorkRunsOnTheBackgroundExecutor() throws Exception {
    RecordingAssociation association = new RecordingAssociation(true);
    ShellAssociationManager manager =
        new ShellAssociationManager(
            List.of(new LimeAssociationOption(association, null, "torrent", "torrent files")));
    List<Runnable> background = new ArrayList<>();
    List<Runnable> edt = new ArrayList<>();

    EventQueue.invokeAndWait(() -> manager.checkAndGrabInBackground(background::add, edt::add));

    assertTrue(association.callers.isEmpty(), "the registry was touched on the EDT");
    Thread worker = new Thread(background.remove(0));
    worker.start();
    worker.join();
    assertFalse(association.callers.isEmpty());
    association.callers.forEach(caller -> assertEquals(worker, caller));
    assertTrue(edt.isEmpty(), "asked the user although every association was available");
  }

  @Test
  void theUsersChoicesAreAppliedOnTheRegistryExecutor() {
    RecordingAssociation chosen = new RecordingAssociation(true);
    RecordingAssociation dropped = new RecordingAssociation(false);
    dropped.registered = true;
    LimeAssociationOption chosenOption = new LimeAssociationOption(chosen, null, "torrent", "t");
    LimeAssociationOption droppedOption = new LimeAssociationOption(dropped, null, "magnet", "m");
    List<Runnable> registry = new ArrayList<>();

    ShellAssociationManager.applyChoices(
        java.util.Map.of(chosenOption, true, droppedOption, false), registry::add);

    assertTrue(chosen.callers.isEmpty() && dropped.callers.isEmpty(), "applied on the caller");
    registry.remove(0).run();
    assertTrue(chosen.calls.contains("register"));
    assertEquals(List.of("unregister"), dropped.calls);
  }

  @Test
  void anAssociationHeldElsewhereAsksTheUserOnTheEdtExecutor() throws Exception {
    RecordingAssociation association = new RecordingAssociation(false);
    ShellAssociationManager manager =
        new ShellAssociationManager(
            List.of(new LimeAssociationOption(association, null, "magnet", "magnet links")));
    List<Runnable> background = new ArrayList<>();
    List<Runnable> edt = new ArrayList<>();

    manager.checkAndGrabInBackground(background::add, edt::add);
    background.remove(0).run();

    assertEquals(1, edt.size(), "the question to the user must go to the EDT executor");
  }
}
