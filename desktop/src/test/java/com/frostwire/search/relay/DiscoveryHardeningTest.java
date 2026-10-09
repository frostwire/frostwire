/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.SessionManager;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class DiscoveryHardeningTest {

  @Test
  void mutableLookupTimeoutsUseDependencySecondsWithoutRoundingUp() {
    RecordingSession session = new RecordingSession();
    byte[] pub = new byte[32];
    new DhtPeerDiscoverySource(session).fetchIdentityEntry(pub);
    assertEquals(5, session.seconds);
    new DhtKarmaChainSource(session).fetchManifest(pub);
    assertEquals(5, session.seconds);
    new RemoteIndexFetcher.DhtIndexSource(session).fetch(pub);
    assertEquals(5, session.seconds);
    new DhtKarmaChainSource(session, 1999).fetchManifest(pub);
    assertEquals(1, session.seconds);
    new DhtPeerDiscoverySource(session, 5, 1999).fetchIdentityEntry(pub);
    assertEquals(1, session.seconds);
    new RemoteIndexFetcher.DhtIndexSource(session, 1999).fetch(pub);
    assertEquals(1, session.seconds);
    int calls = session.calls;
    new DhtKarmaChainSource(session, 999).fetchManifest(pub);
    new DhtPeerDiscoverySource(session, 5, 999).fetchIdentityEntry(pub);
    new RemoteIndexFetcher.DhtIndexSource(session, 999).fetch(pub);
    assertEquals(calls, session.calls);
    Thread.currentThread().interrupt();
    try {
      new DhtPeerDiscoverySource(session).fetchIdentityEntry(pub);
      assertEquals(calls, session.calls);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void rendezvousAlwaysAsksEveryTopicWithinBoundedWaitsAndChecksCancellation() {
    List<Integer> waits = new ArrayList<>();
    SessionManager session =
        new SessionManager() {
          @Override
          public ArrayList<com.frostwire.jlibtorrent.TcpEndpoint> dhtGetPeers(
              com.frostwire.jlibtorrent.Sha1Hash topic, int timeoutSeconds) {
            waits.add(timeoutSeconds);
            return new ArrayList<>();
          }
        };
    DhtPeerDiscoverySource source = new DhtPeerDiscoverySource(session);
    assertTrue(source.fetchEndpoints().isEmpty());
    assertEquals(3, waits.size());
    assertTrue(waits.get(0) <= 2, "the small bootstrap topic does not need a long wait");
    assertTrue(waits.get(1) <= 3);
    assertTrue(waits.get(2) <= 5);
    Thread.currentThread().interrupt();
    try {
      assertTrue(source.fetchEndpoints().isEmpty());
      assertEquals(3, waits.size());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void discoveryBoundsCandidatesAndBindsFetchedRecordToRequestedIdentity() throws Exception {
    IdentityRecord identity = record(keys());
    AtomicInteger authenticated = new AtomicInteger();
    List<DiscoveredEndpoint> endpoints = new ArrayList<>();
    for (int i = 0; i < 100; i++)
      endpoints.add(new DiscoveredEndpoint("peer" + i + ".example", 6888));
    PeerDiscoverySource source =
        new PeerDiscoverySource() {
          @Override
          public List<DiscoveredEndpoint> fetchEndpoints() {
            return endpoints;
          }

          @Override
          public com.frostwire.jlibtorrent.Entry fetchIdentityEntry(byte[] pub) {
            return identity.toEntry();
          }
        };
    try (PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null))) {
      PeerDirectory directory = new PeerDirectory(cache);
      PeerDiscovery discovery =
          new PeerDiscovery(
              source,
              directory,
              (host, port, deadlineNanos) -> {
                authenticated.incrementAndGet();
                return java.util.Optional.empty();
              });
      assertTrue(discovery.discoverAndRegister().isEmpty());
      assertEquals(PeerDiscovery.MAX_CANDIDATES_PER_PASS, authenticated.get());
      assertEquals(0, directory.size());
      assertNull(discovery.fetchIdentityRecord(new byte[32]));
      assertNotNull(discovery.fetchIdentityRecord(identity.ed25519Pub()));
      assertEquals(0, directory.size(), "a signed DHT record alone must not verify an endpoint");
    }
  }

  @Test
  void cachedTrustSelectionDoesNotWaitForColdNativeSourceAndCoalesces() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger fetches = new AtomicInteger();
    RemoteKarmaChainFetcher fetcher =
        new RemoteKarmaChainFetcher(
            pub -> {
              fetches.incrementAndGet();
              started.countDown();
              try {
                release.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return null;
            });
    try (PeerKarmaCache cache = new PeerKarmaCache(fetcher)) {
      PeerDirectory directory = new PeerDirectory(cache);
      byte[] pub = new byte[32];
      directory.upsertVerified(pub, "peer.example", 6888);
      assertTimeoutPreemptively(
          Duration.ofSeconds(1),
          () -> {
            assertEquals(1, directory.topByTrustVerified(1).size());
            assertEquals(1, directory.topByTrust(1).size());
            assertEquals(
                1, directory.sampleVerified(1, Collections.emptySet(), 0, new Random(1)).size());
          });
      assertTrue(started.await(1, TimeUnit.SECONDS));
      for (int i = 0; i < 20; i++) assertEquals(1.0, directory.trustScore(pub));
      assertEquals(1, fetches.get());
      assertTrue(directory.evict(pub));
      assertEquals(0, fetcher.retainedPeerCount());
    } finally {
      release.countDown();
    }
    assertNull(fetcher.fetchChain(new byte[32]));
    assertEquals(0, fetcher.retainedPeerCount());
  }

  @Test
  void negativeCacheIsBoundedExpiresAndClosePreventsRefetch() {
    AtomicLong now = new AtomicLong();
    AtomicInteger calls = new AtomicInteger();
    RemoteKarmaChainFetcher fetcher =
        new RemoteKarmaChainFetcher(
            pub -> {
              calls.incrementAndGet();
              return null;
            },
            4,
            100,
            now::get);
    try {
      byte[] pub = new byte[32];
      fetcher.fetchChain(pub);
      fetcher.fetchChain(pub);
      assertEquals(1, calls.get());
      now.addAndGet(TimeUnit.MILLISECONDS.toNanos(101));
      fetcher.fetchChain(pub);
      assertEquals(2, calls.get());
      for (int i = 0; i < 20; i++) {
        pub[0] = (byte) i;
        fetcher.fetchChain(pub);
        assertTrue(fetcher.retainedPeerCount() <= 4);
      }
    } finally {
      fetcher.close();
    }
    int before = calls.get();
    fetcher.getCachedChain(new byte[32]);
    fetcher.fetchChain(new byte[32]);
    assertEquals(before, calls.get());
    assertEquals(0, fetcher.retainedPeerCount());
  }

  @Test
  void queuedRefreshExpiresWithoutCallingSourceAndReleasesQueueSlot() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    ThreadPoolExecutor worker =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    AtomicInteger calls = new AtomicInteger();
    try (RemoteKarmaChainFetcher fetcher =
        new RemoteKarmaChainFetcher(
            pub -> {
              calls.incrementAndGet();
              return null;
            },
            4,
            1000,
            System::nanoTime,
            100,
            worker)) {
      worker.execute(
          () -> {
            started.countDown();
            try {
              release.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(started.await(1, TimeUnit.SECONDS));
      assertNull(fetcher.getCachedChain(new byte[32]));
      assertEquals(1, worker.getQueue().size());
      // Check the actual queue first: no cache read is needed to reap expired work.
      await(() -> worker.getQueue().isEmpty());
      assertEquals(0, fetcher.retainedPeerCount());
      assertEquals(0, calls.get());
      release.countDown();
      FutureTask<Void> drained = new FutureTask<>(() -> null);
      worker.execute(drained);
      drained.get(1, TimeUnit.SECONDS);
      assertNull(fetcher.fetchChain(new byte[32]));
      assertEquals(1, calls.get());
    } finally {
      release.countDown();
      worker.shutdownNow();
      assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void blockingLookupUsesBoundedWorkerAndIgnoresResultAfterClose() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ThreadPoolExecutor worker =
        new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    try (RemoteKarmaChainFetcher fetcher =
        new RemoteKarmaChainFetcher(
            pub -> {
              entered.countDown();
              // Models a provider that completes cleanup before honoring interruption.
              boolean interrupted = false;
              while (release.getCount() > 0) {
                try {
                  release.await();
                } catch (InterruptedException e) {
                  interrupted = true;
                }
              }
              if (interrupted) Thread.currentThread().interrupt();
              return null;
            },
            4,
            1000,
            System::nanoTime,
            100,
            worker)) {
      assertTimeoutPreemptively(
          Duration.ofSeconds(1), () -> assertNull(fetcher.fetchChain(new byte[32])));
      assertEquals(0, entered.getCount());
      assertEquals(0, fetcher.retainedPeerCount());
      fetcher.close();
      release.countDown();
      worker.shutdown();
      assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
      assertEquals(0, fetcher.retainedPeerCount());
    } finally {
      release.countDown();
      worker.shutdownNow();
      assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void refreshPreservesSpamEndorsementsAndVerifiedRoutes() {
    try (PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null))) {
      PeerDirectory directory = new PeerDirectory(cache);
      byte[] pub = new byte[32];
      byte[] endorser = new byte[32];
      endorser[0] = 1;
      directory.upsertVerified(pub, "verified.example", 6888, 6889);
      directory.addEndorser(pub, endorser);
      directory.markSpam(pub);
      directory.upsertVerified(pub, "refreshed.example", 7888, 7889);
      directory.upsert(pub, "unverified.example", 8888, 8889);
      PeerDirectory.PeerInfo peer = directory.get(pub).orElseThrow();
      assertEquals("refreshed.example", peer.hostname());
      assertTrue(peer.isVerified());
      assertTrue(peer.isSpam());
      assertEquals(1, peer.endorserCount());
      assertEquals(-1.0, directory.trustScore(pub));
      assertTrue(directory.sampleVerified(1, Collections.emptySet(), 0, new Random(1)).isEmpty());
    }
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (!condition.getAsBoolean() && System.nanoTime() - deadline < 0) Thread.sleep(5);
    assertTrue(condition.getAsBoolean());
  }

  private static KeyPair keys() throws Exception {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  private static IdentityRecord record(KeyPair key) {
    return IdentityRecord.createSigned(new byte[20], key, new byte[32], 6888);
  }

  private static final class RecordingSession extends SessionManager {
    int seconds;
    int calls;

    @Override
    public MutableItem dhtGetItem(byte[] pub, byte[] salt, int timeout) {
      seconds = timeout;
      calls++;
      return null;
    }
  }
}
