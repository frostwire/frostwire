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

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Mining the identity during startup held a single-CPU computer for minutes and kept saved
 * downloads waiting (frostwire/frostwire#1534). The identity is now loaded only after the startup
 * sequence, in the background, at low priority.
 */
class DeferredRelayIdentityTest {

  @Test
  void nothingIsMinedUntilStartupFinishesAndThenItRunsOffTheCallerAtLowPriority() throws Exception {
    List<String> events = new CopyOnWriteArrayList<>();
    AtomicReference<Thread> minerThread = new AtomicReference<>();
    AtomicInteger minerPriority = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(1);
    DeferredRelayIdentity deferred =
        new DeferredRelayIdentity(
            () -> {
              events.add("mine");
              minerThread.set(Thread.currentThread());
              minerPriority.set(Thread.currentThread().getPriority());
              return IdentityKeys.generate();
            },
            identity -> {
              events.add("ready");
              ready.countDown();
            });

    // The startup sequence: saved downloads load, the UI becomes responsive.
    events.add("saved downloads loaded");
    events.add("startup finished");
    assertEquals(List.of("saved downloads loaded", "startup finished"), events);

    Thread worker = deferred.startAfterStartup();
    assertTrue(ready.await(10, TimeUnit.SECONDS));

    assertEquals(
        List.of("saved downloads loaded", "startup finished", "mine", "ready"),
        events,
        "the mining runs after startup finished");
    assertNotNull(worker);
    assertSame(worker, minerThread.get());
    assertNotSame(Thread.currentThread(), minerThread.get(), "never on the startup thread");
    assertTrue(minerThread.get().isDaemon());
    assertTrue(
        minerPriority.get() < Thread.NORM_PRIORITY,
        "below the EDT and the workers, so a single CPU serves them first");
  }

  @Test
  @org.junit.jupiter.api.Timeout(30)
  void startingDoesNotWaitForTheMining() throws Exception {
    CountDownLatch mining = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    DeferredRelayIdentity deferred =
        new DeferredRelayIdentity(
            () -> {
              mining.countDown();
              release.await();
              return IdentityKeys.generate();
            },
            identity -> {});

    Thread worker = deferred.startAfterStartup();
    try {
      assertTrue(mining.await(10, TimeUnit.SECONDS));
      assertTrue(worker.isAlive(), "still mining while the caller carries on");
    } finally {
      release.countDown();
      worker.join(10_000);
    }
  }

  @Test
  void theIdentityReachesTheInstallerAndASecondStartDoesNothing() throws Exception {
    IdentityKeys keys = IdentityKeys.generate();
    AtomicInteger loads = new AtomicInteger();
    AtomicReference<IdentityKeys> installed = new AtomicReference<>();
    DeferredRelayIdentity deferred =
        new DeferredRelayIdentity(
            () -> {
              loads.incrementAndGet();
              return keys;
            },
            installed::set);

    Thread worker = deferred.startAfterStartup();
    worker.join(10_000);
    assertNull(deferred.startAfterStartup());

    assertSame(keys, installed.get());
    assertEquals(1, loads.get());
  }

  @Test
  void aFailedMiningInstallsNothingAndDoesNotThrow() throws Exception {
    AtomicInteger installs = new AtomicInteger();
    DeferredRelayIdentity failing =
        new DeferredRelayIdentity(
            () -> {
              throw new java.io.IOException("disk full");
            },
            identity -> installs.incrementAndGet());
    failing.startAfterStartup().join(10_000);

    DeferredRelayIdentity empty =
        new DeferredRelayIdentity(() -> null, identity -> installs.incrementAndGet());
    empty.startAfterStartup().join(10_000);

    DeferredRelayIdentity brokenInstaller =
        new DeferredRelayIdentity(
            IdentityKeys::generate,
            identity -> {
              throw new IllegalStateException("install failed");
            });
    Thread worker = brokenInstaller.startAfterStartup();
    worker.join(10_000);

    assertEquals(0, installs.get());
    assertFalse(worker.isAlive());
  }
}
