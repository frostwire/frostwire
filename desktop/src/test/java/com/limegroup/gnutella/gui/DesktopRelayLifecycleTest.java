/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.search.relay.BitcoinBlockReference;
import com.frostwire.search.relay.BlockHeaderSource;
import com.frostwire.search.relay.DhtAdvertiser;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.KarmaChainCommitScheduler;
import com.frostwire.search.relay.KarmaChainPublisher;
import com.frostwire.search.relay.KarmaChainTable;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.KarmaEndorsementTrigger;
import com.frostwire.search.relay.LocalIndexTable;
import com.limegroup.gnutella.LifecycleManagerImpl;
import com.limegroup.gnutella.LimeCoreGlue;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class DesktopRelayLifecycleTest {
  @TempDir Path temp;

  @Test
  void closeBeforeStartupIsTerminalAndNeverDisposesOnEdt() throws Exception {
    AtomicInteger released = new AtomicInteger();
    AtomicInteger disposed = new AtomicInteger();
    DesktopRelayLifecycle owner =
        new DesktopRelayLifecycle(
            () -> {
              assertFalse(SwingUtilities.isEventDispatchThread());
              released.incrementAndGet();
            },
            () -> {
              assertFalse(SwingUtilities.isEventDispatchThread());
              disposed.incrementAndGet();
            });
    SwingUtilities.invokeAndWait(
        () -> {
          owner.close();
          owner.close();
          assertFalse(owner.getAsBoolean());
          assertThrows(IllegalStateException.class, () -> owner.awaitDisposed(0, TimeUnit.SECONDS));
        });
    assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
    assertFalse(owner.beginStartup());
    assertEquals(1, released.get());
    assertEquals(1, disposed.get());
    try (DesktopRelayLifecycle replacement = new DesktopRelayLifecycle(() -> {}, () -> {})) {
      assertTrue(replacement.beginStartup());
      replacement.finishStartup();
      assertFalse(owner.getAsBoolean(), "a fresh generation cannot revive the stopped owner");
    }
  }

  @Test
  void stopDuringStartupClosesLateWorkersButKeepsBorrowedStoreUntilStartupFinishes()
      throws Exception {
    AtomicInteger released = new AtomicInteger();
    AtomicBoolean storeClosed = new AtomicBoolean();
    AtomicBoolean workerClosed = new AtomicBoolean();
    DesktopRelayLifecycle owner =
        new DesktopRelayLifecycle(released::incrementAndGet, () -> storeClosed.set(true));
    assertTrue(owner.beginStartup());
    try {
      owner.close();
      owner.own(() -> workerClosed.set(true), (timeout, unit) -> workerClosed.get());
      assertTrue(
          workerClosed.get(), "close racing construction must still close the actual worker");
      assertFalse(owner.awaitDisposed(50, TimeUnit.MILLISECONDS));
      assertFalse(storeClosed.get());
      assertEquals(0, released.get());
    } finally {
      owner.finishStartup();
      owner.close();
    }
    assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
    assertTrue(storeClosed.get());
    assertFalse(owner.beginStartup());
  }

  @Test
  void failedDrainDefersDisposalAndRetriesWithoutReleasingResourcesAgain() throws Exception {
    AtomicInteger released = new AtomicInteger();
    AtomicInteger disposed = new AtomicInteger();
    AtomicInteger revoked = new AtomicInteger();
    AtomicBoolean drained = new AtomicBoolean();
    CountDownLatch attempts = new CountDownLatch(2);
    DesktopRelayLifecycle owner =
        new DesktopRelayLifecycle(released::incrementAndGet, disposed::incrementAndGet);
    assertTrue(owner.beginStartup());
    owner.own(
        revoked::incrementAndGet,
        (timeout, unit) -> {
          attempts.countDown();
          return drained.get();
        });
    owner.finishStartup();
    try {
      owner.close();
      assertTrue(attempts.await(3, TimeUnit.SECONDS));
      for (int i = 0; i < 50; i++) {
        owner.close();
        assertFalse(owner.beginStartup());
      }
      assertFalse(owner.awaitDisposed(0, TimeUnit.SECONDS));
      assertEquals(0, disposed.get());
      assertEquals(1, released.get());
      assertEquals(1, revoked.get());
    } finally {
      drained.set(true);
      owner.close();
    }
    assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
    assertEquals(1, disposed.get());
    assertEquals(1, released.get());
  }

  @Test
  void oneDrainDeadlineCoversAllWorkers() throws Exception {
    AtomicLong firstBudget = new AtomicLong();
    AtomicLong secondBudget = new AtomicLong();
    DesktopRelayLifecycle owner = new DesktopRelayLifecycle(() -> {}, () -> {});
    assertTrue(owner.beginStartup());
    owner.own(
        () -> {},
        (timeout, unit) -> {
          firstBudget.set(unit.toNanos(timeout));
          TimeUnit.MILLISECONDS.sleep(100);
          return true;
        });
    owner.own(
        () -> {},
        (timeout, unit) -> {
          secondBudget.set(unit.toNanos(timeout));
          return true;
        });
    owner.finishStartup();
    owner.close();
    assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
    assertTrue(firstBudget.get() <= TimeUnit.SECONDS.toNanos(2));
    assertTrue(firstBudget.get() - secondBudget.get() >= TimeUnit.MILLISECONDS.toNanos(90));
  }

  @Test
  void actualKarmaOwnersStopBeforeDrainAndDoNotRunLaterStagesOrCloseAnInUseStore()
      throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch detached = new CountDownLatch(1);
    AtomicInteger blockReads = new AtomicInteger();
    AtomicInteger sessionReads = new AtomicInteger();
    BlockHeaderSource source =
        new BlockHeaderSource() {
          @Override
          public long getChainTipHeight() {
            entered.countDown();
            awaitBarrier(release);
            return 144;
          }

          @Override
          public BitcoinBlockReference getBlock(long height) {
            blockReads.incrementAndGet();
            return new BitcoinBlockReference(height, new byte[32]);
          }
        };
    try (KarmaChainTable store = KarmaChainTable.open(temp.resolve("karma.db").toFile());
        LocalIndexTable index = LocalIndexTable.open(temp.resolve("index.db").toFile())) {
      DesktopRelayLifecycle owner =
          new DesktopRelayLifecycle(
              () -> {
                assertFalse(SwingUtilities.isEventDispatchThread());
                detached.countDown();
              },
              store::close);
      assertTrue(owner.beginStartup());
      KarmaChainWriter writer = new KarmaChainWriter(identity, source, store, owner);
      owner.own(writer, writer::awaitStopped);
      KarmaEndorsementTrigger trigger =
          new KarmaEndorsementTrigger(index, identity.ed25519PubRaw(), writer, owner);
      owner.own(trigger, trigger::awaitStopped);
      KarmaChainCommitScheduler scheduler =
          new KarmaChainCommitScheduler(
              writer,
              new KarmaChainPublisher(writer, identity),
              60,
              () -> {
                sessionReads.incrementAndGet();
                return null;
              },
              owner);
      owner.own(scheduler, scheduler::awaitStopped);
      DhtAdvertiser advertiser =
          new DhtAdvertiser(
              new IdentityRecordPublisher(identity, 6888),
              null,
              60,
              () -> null,
              true,
              false,
              owner);
      owner.own(advertiser, advertiser::awaitStopped);
      owner.finishStartup();
      try {
        scheduler.start();
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(owner::close);
        assertFalse(owner.getAsBoolean());
        assertTrue(detached.await(3, TimeUnit.SECONDS));
        assertTrue(trigger.awaitStopped(0, TimeUnit.SECONDS), "the real trigger must be closed");
        assertFalse(scheduler.isRunning());
        assertFalse(writer.awaitStopped(0, TimeUnit.SECONDS));
        assertFalse(
            owner.awaitDisposed(2200, TimeUnit.MILLISECONDS), "first drain budget must expire");
        assertTrue(store.isOpen(), "timeout is not permission to close the borrowed store");
        scheduler.start();
        advertiser.start();
        assertFalse(scheduler.isRunning());
        assertFalse(advertiser.isRunning());
      } finally {
        release.countDown();
        owner.close();
        assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
      }
      assertFalse(store.isOpen());
      assertEquals(0, blockReads.get());
      assertEquals(0, sessionReads.get());
      assertTrue(writer.chain().entries().isEmpty());
    }
  }

  @Test
  void actualAdvertiserCannotPublishAfterStoppedSessionResolution() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger puts = new AtomicInteger();
    AtomicBoolean storeClosed = new AtomicBoolean();
    AtomicInteger nativeStops = new AtomicInteger();
    LifecycleManagerImpl manager =
        startedManager(
            () -> {
              assertTrue(storeClosed.get(), "native stop must follow actual owner disposal");
              nativeStops.incrementAndGet();
            });
    SessionManager session =
        new SessionManager() {
          @Override
          public void dhtPutItem(byte[] pub, byte[] key, Entry entry, byte[] salt) {
            puts.incrementAndGet();
          }
        };
    DesktopRelayLifecycle owner = new DesktopRelayLifecycle(() -> {}, () -> storeClosed.set(true));
    assertTrue(owner.beginStartup(manager));
    DhtAdvertiser advertiser =
        new DhtAdvertiser(
            new IdentityRecordPublisher(identity, 6888),
            null,
            60,
            () -> {
              entered.countDown();
              awaitBarrier(release);
              return session;
            },
            true,
            false,
            owner);
    owner.own(advertiser, advertiser::awaitStopped);
    owner.finishStartup();
    Thread shutdown = new Thread(manager::shutdown, "relay-manager-shutdown-test");
    try {
      advertiser.start();
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      shutdown.start();
      assertFalse(owner.awaitDisposed(1200, TimeUnit.MILLISECONDS));
      assertFalse(storeClosed.get());
      assertEquals(0, nativeStops.get());
      assertTrue(shutdown.isAlive(), "one timed-out drain wait must not release native ownership");
    } finally {
      release.countDown();
      owner.close();
      assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
      shutdown.join(3000);
    }
    assertFalse(shutdown.isAlive());
    assertEquals(1, nativeStops.get());
    assertTrue(storeClosed.get());
    assertEquals(0, puts.get());
  }

  @Test
  void rejectedShutdownRegistrationRevokesAndPreventsStartup() throws Exception {
    LifecycleManagerImpl manager = startedManager(() -> {});
    manager.shutdown();
    DesktopRelayLifecycle owner = new DesktopRelayLifecycle(() -> {}, () -> {});
    assertThrows(IllegalStateException.class, () -> owner.beginStartup(manager));
    assertFalse(owner.getAsBoolean());
    assertFalse(owner.beginStartup());
    assertTrue(owner.awaitDisposed(3, TimeUnit.SECONDS));
  }

  private static LifecycleManagerImpl startedManager(Runnable stopEngine) throws Exception {
    // Use the real shutdown manager without starting the GUI or a native session.
    var constructor =
        LifecycleManagerImpl.class.getDeclaredConstructor(LimeCoreGlue.class, Runnable.class);
    constructor.setAccessible(true);
    LifecycleManagerImpl manager = constructor.newInstance(null, stopEngine);
    var begin = LifecycleManagerImpl.class.getDeclaredField("startBegin");
    begin.setAccessible(true);
    ((AtomicBoolean) begin.get(manager)).set(true);
    var latch = LifecycleManagerImpl.class.getDeclaredField("startLatch");
    latch.setAccessible(true);
    ((CountDownLatch) latch.get(manager)).countDown();
    return manager;
  }

  private static void awaitBarrier(CountDownLatch barrier) {
    try {
      assertTrue(barrier.await(10, TimeUnit.SECONDS), "provider barrier timed out");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
