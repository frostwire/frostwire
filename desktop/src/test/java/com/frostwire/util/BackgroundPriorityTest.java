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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Mining the IceBridge identity on the startup thread at normal priority took the only CPU of a
 * single-CPU computer for minutes on a first run, starving the EDT (Icebase #9565a0353a1c,
 * #44c4ce972781, frostwire/frostwire#1534).
 */
class BackgroundPriorityTest {
  @Test
  void theWorkRunsBelowNormalPriorityAndThePriorityIsRestored() throws Exception {
    AtomicInteger during = new AtomicInteger();
    int[] after = new int[1];
    Thread thread =
        new Thread(
            () -> {
              try {
                assertEquals(
                    "done",
                    BackgroundPriority.call(
                        () -> {
                          during.set(Thread.currentThread().getPriority());
                          return "done";
                        }));
              } catch (Exception e) {
                throw new AssertionError(e);
              }
              after[0] = Thread.currentThread().getPriority();
            });
    thread.setPriority(Thread.NORM_PRIORITY);
    thread.start();
    thread.join();

    assertEquals(BackgroundPriority.PRIORITY, during.get());
    assertTrue(during.get() < Thread.NORM_PRIORITY, "below the EDT and the workers");
    assertEquals(Thread.NORM_PRIORITY, after[0]);
  }

  @Test
  void thePriorityIsRestoredWhenTheWorkThrows() throws Exception {
    int[] after = new int[1];
    Thread thread =
        new Thread(
            () -> {
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      BackgroundPriority.call(
                          () -> {
                            throw new IllegalStateException("mining failed");
                          }));
              after[0] = Thread.currentThread().getPriority();
            });
    thread.setPriority(Thread.NORM_PRIORITY);
    thread.start();
    thread.join();

    assertEquals(Thread.NORM_PRIORITY, after[0]);
  }

  @Test
  void aThreadAlreadyBelowThatPriorityKeepsItsOwn() throws Exception {
    AtomicInteger during = new AtomicInteger();
    Thread thread =
        new Thread(
            () -> {
              try {
                BackgroundPriority.call(
                    () -> {
                      during.set(Thread.currentThread().getPriority());
                      return null;
                    });
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            });
    thread.setPriority(Thread.MIN_PRIORITY);
    thread.start();
    thread.join();

    assertEquals(Thread.MIN_PRIORITY, during.get());
  }

  @Test
  void theDesktopMinesItsIdentityAtBackgroundPriority() throws Exception {
    // Since #1534 the identity is loaded (or mined) by DeferredRelayIdentity, after startup, on its
    // own thread; the mining itself must still go through BackgroundPriority.
    String deferred =
        Files.readString(
                Path.of("src/main/java/com/limegroup/gnutella/gui/DeferredRelayIdentity.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        deferred.contains("BackgroundPriority.call(loader::load)"),
        "the identity must not be mined at normal priority");

    String initializer =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/Initializer.java"))
            .replaceAll("\\s+", "");
    assertTrue(
        initializer.contains("()->IdentityKeys.loadOrCreate(identityFile)"),
        "Initializer must hand the real loader to DeferredRelayIdentity");
    assertFalse(
        initializer.contains("BackgroundPriority.call(()->IdentityKeys.loadOrCreate"),
        "Initializer must not mine the identity itself, on the startup thread");
  }
}
