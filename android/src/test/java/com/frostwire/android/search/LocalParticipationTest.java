/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.frostwire.search.relay.BitcoinBlockReference;
import com.frostwire.search.relay.BlockHeaderSource;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaChainStore;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.KarmaConstants;
import com.frostwire.search.relay.RelaySearchWiring;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1416: the Distributed Search preferences show the local identity's
 * participation, the endorsements its karma chain gave, next to the karma it received.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LocalParticipationTest {

  private static final long HEIGHT = 6700L * KarmaConstants.BLOCKS_PER_EPOCH;

  @Test
  public void localParticipationCountsTheEndorsementsTheLocalChainGave() throws Exception {
    RelaySearchWiring wiring = new RelaySearchWiring();
    assertNull("no relay stack, no participation", wiring.localParticipation());

    try (KarmaChainWriter writer = new KarmaChainWriter(identity(), bitcoin(), emptyStore())) {
      wiring.karmaChainWriter(writer);
      assertEquals(Long.valueOf(0), wiring.localParticipation());

      byte[] peer = new byte[32];
      peer[0] = 1;
      assertTrue(writer.endorseSourcePeer(peer, new byte[20]));

      assertEquals(Long.valueOf(1), wiring.localParticipation());
    }
  }

  private static IdentityKeys identity() throws Exception {
    KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    byte[] enc = kp.getPrivate().getEncoded();
    byte[] pub = kp.getPublic().getEncoded();
    return TestIdentityKeys.fromSeedWithoutNative(
        Arrays.copyOfRange(enc, enc.length - 32, enc.length),
        Arrays.copyOfRange(pub, pub.length - 32, pub.length));
  }

  private static BlockHeaderSource bitcoin() {
    return new BlockHeaderSource() {
      @Override
      public BitcoinBlockReference getBlock(long height) {
        return new BitcoinBlockReference(height, new byte[32]);
      }

      @Override
      public long getChainTipHeight() {
        return HEIGHT + KarmaConstants.ANCHOR_DEPTH_BLOCKS;
      }
    };
  }

  private static KarmaChainStore emptyStore() {
    return new KarmaChainStore() {
      @Override
      public void append(KarmaChainEntry entry) {}

      @Override
      public KarmaChain loadChain(byte[] ownerPub) {
        return new KarmaChain(ownerPub);
      }

      @Override
      public void close() {}
    };
  }
}
