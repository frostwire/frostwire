/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.DisplayKarma;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.limegroup.gnutella.gui.I18n;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1418: the Identity pane shows the local identity's karma as every other
 * indicator does ({@link PeerDirectory#displayKarma}), read off the EDT. It used to open the karma
 * chain database on the EDT and show the count of endorsements given.
 */
class IdentityLocalKarmaTest {

  private static final Executor OFF_EDT = r -> new Thread(r, "identity-karma-test").start();

  @Test
  void showsTheDisplayKarmaReadOffTheEdt() throws Exception {
    RecordingKarmaCache karmaCache = new RecordingKarmaCache();
    PeerDirectory directory = new PeerDirectory(karmaCache);
    byte[] ownPub = new byte[32];
    ownPub[31] = 4;
    directory.upsert(ownPub, "me.example", 6881);
    karmaCache.score = 2;

    Shown shown = load(ownPub, directory);

    assertEquals(DisplayKarma.of(directory.displayKarma(ownPub)).text(), shown.text);
    assertEquals(DisplayKarma.of(3).text(), shown.text); // 1 direct trust + 2 karma
    assertTrue(shown.onEdt, "the label must be set on the EDT");
    synchronized (karmaCache.onEdt) {
      assertFalse(karmaCache.onEdt.isEmpty());
      assertFalse(karmaCache.onEdt.contains(Boolean.TRUE), "karma read on the EDT");
    }
  }

  @Test
  void theLocalIdentityIsNotInItsOwnDirectorySoItShowsZeroToday() throws Exception {
    PeerDirectory directory = new PeerDirectory(new RecordingKarmaCache());
    byte[] ownPub = new byte[32];

    assertEquals(DisplayKarma.of(0).text(), load(ownPub, directory).text);
  }

  @Test
  void withoutTheDistributedSearchStackItIsNotAvailable() throws Exception {
    assertEquals(I18n.tr("(not available)"), load(new byte[32], null).text);
  }

  private static Shown load(byte[] ownPub, PeerDirectory directory) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Shown shown = new Shown();
    AtomicReference<String> beforeReturn = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          IdentitySettingsPaneItem.loadLocalKarma(
              ownPub,
              () -> directory,
              OFF_EDT,
              text -> {
                shown.text = text;
                shown.onEdt = SwingUtilities.isEventDispatchThread();
                done.countDown();
              });
          beforeReturn.set(shown.text);
        });
    assertTrue(done.await(10, TimeUnit.SECONDS), "the karma never showed");
    assertNull(beforeReturn.get(), "the EDT must not wait for the karma");
    return shown;
  }

  private static final class Shown {
    volatile String text;
    volatile boolean onEdt;
  }

  /** Records whether each karma read through the directory ran on the EDT. */
  private static final class RecordingKarmaCache extends PeerKarmaCache {
    final List<Boolean> onEdt = new ArrayList<>();
    volatile long score;

    RecordingKarmaCache() {
      super(new RemoteKarmaChainFetcher(peerPub -> null));
    }

    @Override
    public long getCachedKarma(byte[] peerPub) {
      synchronized (onEdt) {
        onEdt.add(SwingUtilities.isEventDispatchThread());
      }
      return score;
    }
  }
}
