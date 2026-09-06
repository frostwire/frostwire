/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.bittorrent.BTEngineListener;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BTEngineListenerChainTest {

  @Test
  void enginePublishesListenerAndDispatchDoesNotHoldRegistrationMonitor() {
    assertDoesNotThrow(
        () ->
            assertTrue(
                Modifier.isVolatile(BTEngine.class.getDeclaredField("listener").getModifiers())));
    for (String event : List.of("fireStarted", "fireStopped")) {
      BTEngine engine = newEngine();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger calls = new AtomicInteger();
      BTEngineListener listener =
          new BTEngineAdapter() {
            private void onEvent() {
              assertFalse(Thread.holdsLock(engine));
              calls.incrementAndGet();
              entered.countDown();
              assertDoesNotThrow(() -> assertTrue(release.await(5, TimeUnit.SECONDS)));
            }

            @Override
            public void started(BTEngine ignored) {
              onEvent();
            }

            @Override
            public void stopped(BTEngine ignored) {
              onEvent();
            }
          };
      BTEngineListenerChain.install(engine, listener);
      FutureTask<Void> dispatch =
          new FutureTask<>(
              () -> {
                fire(engine, event);
                return null;
              });
      Thread thread = new Thread(dispatch, "listener-dispatch-test");
      thread.start();
      try {
        assertDoesNotThrow(() -> assertTrue(entered.await(5, TimeUnit.SECONDS)));
        FutureTask<Boolean> removal =
            new FutureTask<>(() -> BTEngineListenerChain.remove(engine, listener));
        new Thread(removal, "listener-removal-test").start();
        assertTrue(assertDoesNotThrow(() -> removal.get(5, TimeUnit.SECONDS)));
        assertNull(engine.getListener());
        // A second event sees removal while the first callback still owns its snapshot.
        fire(engine, event);
        assertEquals(1, calls.get());
      } finally {
        release.countDown();
        assertDoesNotThrow(() -> dispatch.get(5, TimeUnit.SECONDS));
      }
    }
  }

  @Test
  void removingDelegateDuringDispatchPreservesTheInFlightChainSnapshot() {
    BTEngine engine = newEngine();
    RecordingListener removed = new RecordingListener();
    BTEngineListener first =
        new BTEngineAdapter() {
          @Override
          public void started(BTEngine ignored) {
            BTEngineListenerChain.remove(engine, removed);
          }
        };
    engine.setListener(new BTEngineListenerChain(first, removed));
    fire(engine, "fireStarted");
    assertEquals(1, removed.events.size());
    fire(engine, "fireStarted");
    assertEquals(1, removed.events.size());
    assertSame(first, engine.getListener());
  }

  private static BTEngine newEngine() {
    return assertDoesNotThrow(
        () -> {
          var constructor = BTEngine.class.getDeclaredConstructor();
          constructor.setAccessible(true);
          return constructor.newInstance();
        });
  }

  private static void fire(BTEngine engine, String event) {
    assertDoesNotThrow(
        () -> {
          var method = BTEngine.class.getDeclaredMethod(event);
          method.setAccessible(true);
          method.invoke(engine);
        });
  }

  @Test
  void removalPreservesOtherOwnersAndIsIdempotent() {
    BTEngine engine =
        assertDoesNotThrow(
            () -> {
              java.lang.reflect.Constructor<BTEngine> constructor =
                  BTEngine.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              return constructor.newInstance();
            });
    RecordingListener a = new RecordingListener();
    RecordingListener b = new RecordingListener();
    RecordingListener c = new RecordingListener();
    engine.setListener(new BTEngineListenerChain(a, new BTEngineListenerChain(b, c)));
    assertTrue(BTEngineListenerChain.remove(engine, b));
    engine.getListener().started(null);
    assertEquals(1, a.events.size());
    assertEquals(0, b.events.size());
    assertEquals(1, c.events.size());
    assertFalse(BTEngineListenerChain.remove(engine, b));
    assertTrue(BTEngineListenerChain.remove(engine, a));
    assertSame(c, engine.getListener());
    assertTrue(BTEngineListenerChain.remove(engine, c));
    assertNull(engine.getListener());
    BTEngineListenerChain.install(engine, b);
    assertSame(b, engine.getListener());
  }

  @Test
  void chainFansOutEventsToAllDelegates() {
    RecordingListener a = new RecordingListener();
    RecordingListener b = new RecordingListener();
    BTEngineListenerChain chain = new BTEngineListenerChain(a, b);

    BTEngine engine = null;
    chain.started(engine);
    chain.stopped(engine);
    chain.downloadAdded(engine, null);
    chain.downloadUpdate(engine, null);

    assertEquals(4, a.events.size());
    assertEquals(4, b.events.size());
    assertEquals("started", a.events.get(0));
    assertEquals("downloadUpdate", b.events.get(3));
  }

  @Test
  void throwingListenerDoesNotStopChain() {
    RecordingListener a = new RecordingListener();
    BTEngineListener throwing =
        new BTEngineAdapter() {
          @Override
          public void downloadAdded(BTEngine engine, BTDownload dl) {
            throw new IllegalStateException("boom");
          }
        };
    RecordingListener b = new RecordingListener();
    BTEngineListenerChain chain = new BTEngineListenerChain(a, throwing, b);

    chain.downloadAdded(null, null);

    assertTrue(a.events.contains("downloadAdded"));
    assertTrue(b.events.contains("downloadAdded"));
  }

  @Test
  void withAppendsListenerAndPreservesOrder() {
    RecordingListener a = new RecordingListener();
    RecordingListener b = new RecordingListener();
    RecordingListener c = new RecordingListener();
    BTEngineListenerChain chain = new BTEngineListenerChain(a, b).with(c);

    chain.downloadAdded(null, null);

    assertEquals(1, a.events.size());
    assertEquals(1, b.events.size());
    assertEquals(1, c.events.size());
  }

  @Test
  void withRejectsNull() {
    BTEngineListenerChain chain = new BTEngineListenerChain();
    assertThrows(IllegalArgumentException.class, () -> chain.with(null));
  }

  @Test
  void constructorRejectsNullArray() {
    assertThrows(
        NullPointerException.class, () -> new BTEngineListenerChain((BTEngineListener[]) null));
  }

  @Test
  void nullDelegateIsSkipped() {
    RecordingListener a = new RecordingListener();
    BTEngineListenerChain chain = new BTEngineListenerChain(a, null);

    chain.downloadAdded(null, null);

    assertEquals(1, a.events.size());
  }

  @Test
  void withDoesNotAddDuplicateListener() {
    RecordingListener a = new RecordingListener();
    BTEngineListenerChain chain = new BTEngineListenerChain(a);
    BTEngineListenerChain chain2 = chain.with(a);

    assertSame(chain, chain2);
    assertEquals(1, chain2.size());
  }

  private static final class RecordingListener extends BTEngineAdapter {
    final List<String> events = new ArrayList<>();

    @Override
    public void started(BTEngine engine) {
      events.add("started");
    }

    @Override
    public void stopped(BTEngine engine) {
      events.add("stopped");
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
      events.add("downloadAdded");
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
      events.add("downloadUpdate");
    }
  }
}
