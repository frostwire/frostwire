/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Received, trust-weighted karma (frostwire/frostwire#1417): a peer's karma is what others endorsed
 * it with, one vote per endorser, weighted by the trust this node has in the endorser, for torrents
 * the peer announced itself; endorsing others raises nobody's own karma.
 */
class ReceivedKarmaTest {

  private static final long EPOCH = 6700;
  private static final double EPS = 1e-9;

  @TempDir Path dir;

  private IdentityKeys self;
  private ReceivedKarma karma;
  private int torrents;

  @BeforeEach
  void setUp() {
    self = PowTestIdentities.get(0);
    karma = new ReceivedKarma();
    karma.setSelf(self.ed25519PubRaw(), infoHash -> false);
  }

  @Test
  void endorsingOthersRaisesTheirKarmaNotYours() {
    IdentityKeys endorsee = PowTestIdentities.get(1);
    IdentityKeys generous = PowTestIdentities.get(2);
    karma.indexChain(
        self.ed25519PubRaw(), chain(self, EPOCH, endorsement(endorsee, announcedBy(endorsee))));
    karma.indexChain(
        generous.ed25519PubRaw(),
        chain(
            generous,
            EPOCH,
            endorsement(PowTestIdentities.get(3), announcedBy(PowTestIdentities.get(3))),
            endorsement(PowTestIdentities.get(4), announcedBy(PowTestIdentities.get(4))),
            endorsement(PowTestIdentities.get(5), announcedBy(PowTestIdentities.get(5)))));

    assertEquals(1.0, karma.score(endorsee.ed25519PubRaw()), EPS, "this node's own vote");
    assertEquals(0, karma.score(self.ed25519PubRaw()), EPS, "giving is not receiving");
    assertEquals(0, karma.score(generous.ed25519PubRaw()), EPS, "three given, none received");
  }

  @Test
  void trustFlowsFromThisNodeAndHalvesPerHop() {
    IdentityKeys a = PowTestIdentities.get(1);
    IdentityKeys b = PowTestIdentities.get(2);
    IdentityKeys c = PowTestIdentities.get(3);
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH, endorsement(a, announcedBy(a))));
    karma.indexChain(a.ed25519PubRaw(), chain(a, EPOCH, endorsement(b, announcedBy(b))));
    karma.indexChain(b.ed25519PubRaw(), chain(b, EPOCH, endorsement(c, announcedBy(c))));

    assertEquals(1.0, karma.score(a.ed25519PubRaw()), EPS);
    assertEquals(0.5, karma.score(b.ed25519PubRaw()), EPS, "a's trust: 1 * DECAY");
    assertEquals(0.25, karma.score(c.ed25519PubRaw()), EPS, "b's trust: a's * DECAY");
  }

  @Test
  void aStrangerCountsOnlyAfterThisNodeHasKnownItForTheMinimumAge() {
    IdentityKeys stranger = PowTestIdentities.get(1);
    IdentityKeys endorsee = PowTestIdentities.get(2);
    byte[] torrent = announcedBy(endorsee);
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH));
    karma.indexChain(
        stranger.ed25519PubRaw(), chain(stranger, EPOCH, endorsement(endorsee, torrent)));
    assertEquals(0, karma.score(endorsee.ed25519PubRaw()), EPS, "first seen just now");

    long later = EPOCH + ReceivedKarma.MIN_ENDORSER_AGE_EPOCHS;
    karma.indexChain(self.ed25519PubRaw(), chain(self, later));

    double fresh = Math.pow(0.5, ReceivedKarma.MIN_ENDORSER_AGE_EPOCHS / 30.0);
    assertEquals(ReceivedKarma.STRANGER_WEIGHT * fresh, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void theClockAgesStrangersAndVotesWithoutNewChains() {
    java.util.concurrent.atomic.AtomicLong epoch =
        new java.util.concurrent.atomic.AtomicLong(EPOCH);
    karma.setClock(epoch::get);
    IdentityKeys stranger = PowTestIdentities.get(1);
    IdentityKeys endorsee = PowTestIdentities.get(2);
    karma.indexChain(
        stranger.ed25519PubRaw(),
        chain(stranger, EPOCH, endorsement(endorsee, announcedBy(endorsee))));
    assertEquals(0, karma.score(endorsee.ed25519PubRaw()), EPS);

    epoch.set(EPOCH + ReceivedKarma.HALF_LIFE_EPOCHS);

    assertEquals(ReceivedKarma.STRANGER_WEIGHT * 0.5, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void aChainsOwnEpochsDoNotAgeItsOwner() {
    IdentityKeys stranger = PowTestIdentities.get(1);
    IdentityKeys endorsee = PowTestIdentities.get(2);
    // Signed at old heights (anyone can), but first seen now.
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH + 100));
    karma.indexChain(
        stranger.ed25519PubRaw(),
        chain(stranger, EPOCH, endorsement(endorsee, announcedBy(endorsee))));

    assertEquals(0, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void aClusterNobodyTrustedEndorsesGetsOnlyStrangerVotes() {
    List<IdentityKeys> sybils = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      sybils.add(PowTestIdentities.get(i));
    }
    IdentityKeys target = PowTestIdentities.get(6);
    byte[] targetTorrent = announcedBy(target);
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH));
    for (IdentityKeys sybil : sybils) {
      List<Object[]> endorsements = new ArrayList<>();
      endorsements.add(endorsement(target, targetTorrent));
      for (IdentityKeys other : sybils) {
        if (other != sybil) {
          endorsements.add(endorsement(other, announcedBy(other)));
        }
      }
      karma.indexChain(
          sybil.ed25519PubRaw(), chain(sybil, EPOCH, endorsements.toArray(new Object[0][])));
    }
    karma.indexChain(
        self.ed25519PubRaw(), chain(self, EPOCH + ReceivedKarma.MIN_ENDORSER_AGE_EPOCHS));

    double fresh = Math.pow(0.5, ReceivedKarma.MIN_ENDORSER_AGE_EPOCHS / 30.0);
    // Five strangers, each worth 0.1, however much they endorse each other.
    assertEquals(
        5 * ReceivedKarma.STRANGER_WEIGHT * fresh, karma.score(target.ed25519PubRaw()), EPS);
    assertTrue(karma.score(target.ed25519PubRaw()) < 1.0, "less than one trusted vote");
  }

  @Test
  void anEndorserVotesOnceForAPeerHoweverManyTorrents() {
    IdentityKeys endorsee = PowTestIdentities.get(1);
    karma.indexChain(
        self.ed25519PubRaw(),
        chain(
            self,
            EPOCH,
            endorsement(endorsee, announcedBy(endorsee)),
            endorsement(endorsee, announcedBy(endorsee)),
            endorsement(endorsee, announcedBy(endorsee))));

    assertEquals(1.0, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void onlyTorrentsTheEndorseeAnnouncedCount() {
    IdentityKeys endorsee = PowTestIdentities.get(1);
    byte[] torrent = torrent();
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH, endorsement(endorsee, torrent)));
    assertEquals(0, karma.score(endorsee.ed25519PubRaw()), EPS, "never seen it announce that");

    karma.noteAnnounced(PowTestIdentities.get(2).ed25519PubRaw(), torrent);
    assertEquals(0, karma.score(endorsee.ed25519PubRaw()), EPS, "someone else announced it");

    karma.noteAnnounced(endorsee.ed25519PubRaw(), torrent);
    assertEquals(1.0, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void endorsersWithoutProofOfWorkAreIgnored() throws Exception {
    IdentityKeys cheap = IdentityKeys.generate(0);
    if (IdentityKeys.meetsIdentityDifficulty(cheap.ed25519PubRaw())) {
      cheap = IdentityKeys.generate(0);
    }
    IdentityKeys endorsee = PowTestIdentities.get(1);
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH));
    karma.indexChain(
        cheap.ed25519PubRaw(), chain(cheap, EPOCH, endorsement(endorsee, announcedBy(endorsee))));
    karma.indexChain(
        self.ed25519PubRaw(), chain(self, EPOCH + ReceivedKarma.MIN_ENDORSER_AGE_EPOCHS));

    assertEquals(0, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void votesHalveEveryHalfLife() {
    IdentityKeys endorsee = PowTestIdentities.get(1);
    karma.indexChain(
        self.ed25519PubRaw(), chain(self, EPOCH, endorsement(endorsee, announcedBy(endorsee))));
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH + ReceivedKarma.HALF_LIFE_EPOCHS));

    assertEquals(0.5, karma.score(endorsee.ed25519PubRaw()), EPS);
  }

  @Test
  void endorsementsOfThisNodeCountForTorrentsInItsOwnIndex() {
    byte[] shared = torrent();
    byte[] notShared = torrent();
    karma.setSelf(self.ed25519PubRaw(), infoHash -> Arrays.equals(infoHash, shared));
    IdentityKeys a = PowTestIdentities.get(1);
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH, endorsement(a, announcedBy(a))));
    karma.indexChain(a.ed25519PubRaw(), chain(a, EPOCH, endorsement(self, notShared)));
    assertEquals(0, karma.score(self.ed25519PubRaw()), EPS, "not in this node's index");

    karma.indexChain(a.ed25519PubRaw(), chain(a, EPOCH, endorsement(self, shared)));

    assertEquals(0.5, karma.score(self.ed25519PubRaw()), EPS, "a, trusted 0.5, endorsed us");
  }

  @Test
  void firstSightingsSurviveARestart() {
    File file = dir.resolve("karma_first_seen.txt").toFile();
    IdentityKeys stranger = PowTestIdentities.get(1);
    IdentityKeys endorsee = PowTestIdentities.get(2);
    byte[] torrent = announcedBy(endorsee);
    karma.persistTo(file);
    karma.indexChain(self.ed25519PubRaw(), chain(self, EPOCH));
    karma.indexChain(
        stranger.ed25519PubRaw(), chain(stranger, EPOCH, endorsement(endorsee, torrent)));
    karma.save();
    assertTrue(file.isFile());

    ReceivedKarma restarted = new ReceivedKarma();
    restarted.setSelf(self.ed25519PubRaw(), infoHash -> false);
    restarted.persistTo(file);
    restarted.noteAnnounced(endorsee.ed25519PubRaw(), torrent);
    long later = EPOCH + ReceivedKarma.MIN_ENDORSER_AGE_EPOCHS;
    restarted.indexChain(self.ed25519PubRaw(), chain(self, later));
    restarted.indexChain(
        stranger.ed25519PubRaw(), chain(stranger, EPOCH, endorsement(endorsee, torrent)));

    assertTrue(restarted.score(endorsee.ed25519PubRaw()) > 0, "known since before the restart");
  }

  @Test
  void aDamagedFirstSightingsFileStartsEmpty() throws Exception {
    File file = dir.resolve("karma_first_seen.txt").toFile();
    java.nio.file.Files.write(
        file.toPath(),
        "zz 12\n\u0000\u0001 nope\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

    karma.persistTo(file);

    assertEquals(0, karma.score(PowTestIdentities.get(1).ed25519PubRaw()), EPS);
    assertFalse(Double.isNaN(karma.score(null)));
  }

  @Test
  void everyChainTheKarmaCacheVerifiesIsIndexed() {
    IdentityKeys a = PowTestIdentities.get(1);
    IdentityKeys b = PowTestIdentities.get(2);
    List<KarmaChainEntry> aChain = chain(a, EPOCH, endorsement(b, torrent()));
    byte[] head = KarmaChainPages.paginate(aChain).get(0);
    try (PeerKarmaCache cache =
        new PeerKarmaCache(
            new RemoteKarmaChainFetcher(
                pub ->
                    Arrays.equals(pub, a.ed25519PubRaw())
                        ? com.frostwire.jlibtorrent.Entry.bdecode(head)
                        : null))) {
      ReceivedKarma received = cache.received();
      received.setSelf(self.ed25519PubRaw(), infoHash -> false);
      received.noteAnnounced(b.ed25519PubRaw(), aChain.get(1).infoHash());
      byte[] aTorrent = torrent();
      received.noteAnnounced(a.ed25519PubRaw(), aTorrent);
      received.indexChain(self.ed25519PubRaw(), chain(self, EPOCH, endorsement(a, aTorrent)));

      assertEquals(1, cache.participation(a.ed25519PubRaw()), "a gave one endorsement");

      assertEquals(1.0, cache.receivedKarma(a.ed25519PubRaw()), EPS, "endorsed by this node");
      assertEquals(0.5, cache.receivedKarma(b.ed25519PubRaw()), EPS, "endorsed by a");
    }
  }

  @Test
  void theLocalNodesEndorsementsAreIndexedAsTheyAreWritten() {
    IdentityKeys endorsee = PowTestIdentities.get(1);
    long height = EPOCH * KarmaConstants.BLOCKS_PER_EPOCH;
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
    File relayHome = dir.toFile();
    try (KarmaChainWriter writer = new KarmaChainWriter(self, bitcoin, store);
        PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null))) {
      cache.trackLocalNode(self.ed25519PubRaw(), writer, new EmptyLocalIndex(), relayHome);
      byte[] torrent = torrent();
      cache.received().noteAnnounced(endorsee.ed25519PubRaw(), torrent);
      assertEquals(0, cache.receivedKarma(endorsee.ed25519PubRaw()), EPS);

      assertTrue(writer.endorseSourcePeer(endorsee.ed25519PubRaw(), torrent));

      assertEquals(1.0, cache.receivedKarma(endorsee.ed25519PubRaw()), EPS);
    }
    assertTrue(new File(relayHome, RelayConstants.KARMA_FIRST_SEEN_FILE).isFile());
  }

  // --- helpers ---

  private byte[] torrent() {
    byte[] infoHash = new byte[20];
    infoHash[0] = (byte) ++torrents;
    infoHash[1] = (byte) (torrents >> 8);
    return infoHash;
  }

  /** A new torrent {@code peer} announced in a response this node verified. */
  private byte[] announcedBy(IdentityKeys peer) {
    byte[] infoHash = torrent();
    karma.noteAnnounced(peer.ed25519PubRaw(), infoHash);
    return infoHash;
  }

  private static Object[] endorsement(IdentityKeys endorsee, byte[] infoHash) {
    return new Object[] {endorsee.ed25519PubRaw(), infoHash};
  }

  /** {@code owner}'s chain: a commitment at {@code epoch}, then the endorsements, in that epoch. */
  private static List<KarmaChainEntry> chain(
      IdentityKeys owner, long epoch, Object[]... endorsements) {
    KarmaChain chain = new KarmaChain(owner.ed25519PubRaw());
    chain.commitEpoch(block(epoch, 0), owner.ed25519().getPrivate());
    for (int i = 0; i < endorsements.length; i++) {
      chain.endorse(
          (byte[]) endorsements[i][0],
          (byte[]) endorsements[i][1],
          block(epoch, i + 1),
          owner.ed25519().getPrivate());
    }
    return chain.entries();
  }

  private static BitcoinBlockReference block(long epoch, int offset) {
    long height = epoch * KarmaConstants.BLOCKS_PER_EPOCH + offset;
    byte[] hash = new byte[32];
    hash[0] = (byte) height;
    hash[1] = (byte) (height >> 8);
    return new BitcoinBlockReference(height, hash);
  }
}
