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

import com.frostwire.search.relay.BitcoinBlockReference;
import com.frostwire.search.relay.BlockHeaderSource;
import com.frostwire.search.relay.DisplayKarma;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaChainStore;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.KarmaConstants;
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
import java.util.function.Supplier;
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
    assertEquals(DisplayKarma.of(2).text(), shown.text); // the karma it received
    assertTrue(shown.onEdt, "the label must be set on the EDT");
    synchronized (karmaCache.onEdt) {
      assertFalse(karmaCache.onEdt.isEmpty());
      assertFalse(karmaCache.onEdt.contains(Boolean.TRUE), "karma read on the EDT");
    }
  }

  @Test
  void theLocalIdentityShowsItsReceivedKarmaThoughItIsNotInItsOwnDirectory() throws Exception {
    RecordingKarmaCache karmaCache = new RecordingKarmaCache();
    PeerDirectory directory = new PeerDirectory(karmaCache);
    karmaCache.score = 2;
    byte[] ownPub = new byte[32];

    // frostwire/frostwire#1417: received karma, not the trust score, which is 0 outside it.
    assertEquals(DisplayKarma.of(2).text(), load(ownPub, directory).text);
  }

  @Test
  void withoutTheDistributedSearchStackItIsNotAvailable() throws Exception {
    assertEquals(I18n.tr("(not available)"), load(new byte[32], null).text);
  }

  @Test
  void participationIsTheEndorsementsTheLocalChainGave() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    long height = 6700L * KarmaConstants.BLOCKS_PER_EPOCH;
    BlockHeaderSource bitcoin =
        new BlockHeaderSource() {
          @Override
          public BitcoinBlockReference getBlock(long h) {
            return new BitcoinBlockReference(h, new byte[32]);
          }

          @Override
          public long getChainTipHeight() {
            return height + KarmaConstants.ANCHOR_DEPTH_BLOCKS;
          }
        };
    KarmaChainStore store =
        new KarmaChainStore() {
          @Override
          public void append(KarmaChainEntry entry) {}

          @Override
          public KarmaChain loadChain(byte[] ownerPub) {
            return new KarmaChain(ownerPub);
          }

          @Override
          public void close() {}
        };
    try (KarmaChainWriter writer = new KarmaChainWriter(identity, bitcoin, store)) {
      byte[] peer = new byte[32];
      peer[0] = 1;
      byte[] otherTorrent = new byte[20];
      otherTorrent[0] = 1;
      assertTrue(writer.endorseSourcePeer(peer, new byte[20]));
      assertTrue(writer.endorseSourcePeer(peer, otherTorrent));

      Shown shown = loadParticipation(() -> writer);

      assertEquals(I18n.trn("{0} endorsement given", "{0} endorsements given", 2, 2), shown.text);
      assertTrue(shown.onEdt);
    }
    assertEquals(I18n.tr("(not available)"), loadParticipation(() -> null).text);
  }

  private static Shown loadParticipation(Supplier<KarmaChainWriter> writer) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    Shown shown = new Shown();
    AtomicReference<Boolean> readOnEdt = new AtomicReference<>();
    IdentitySettingsPaneItem.loadParticipation(
        () -> {
          readOnEdt.set(SwingUtilities.isEventDispatchThread());
          return writer.get();
        },
        OFF_EDT,
        text -> {
          shown.text = text;
          shown.onEdt = SwingUtilities.isEventDispatchThread();
          done.countDown();
        });
    assertTrue(done.await(10, TimeUnit.SECONDS), "the participation never showed");
    assertFalse(readOnEdt.get(), "the chain must not be read on the EDT");
    return shown;
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
    public double receivedKarma(byte[] peerPub) {
      synchronized (onEdt) {
        onEdt.add(SwingUtilities.isEventDispatchThread());
      }
      return score;
    }
  }
}
