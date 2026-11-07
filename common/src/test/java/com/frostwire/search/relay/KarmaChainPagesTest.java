/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.Bencode;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The paged, compact form of a published karma chain (frostwire/frostwire#1416, question 5): a page
 * holds several entries, the head verifies on its own, older pages extend it back while they link,
 * and anything malformed is refused. Version 1 manifests, which released clients publish, still
 * read.
 */
class KarmaChainPagesTest {

  private static final long EPOCH = KarmaConstants.epochForHeight(960_000L);

  private static byte[] owner;
  private static PrivateKey key;
  private static byte[] other;
  private static PrivateKey otherKey;

  @BeforeAll
  static void identities() throws Exception {
    IdentityKeys id = IdentityKeys.generate(0);
    owner = id.ed25519PubRaw();
    key = id.ed25519().getPrivate();
    IdentityKeys second = IdentityKeys.generate(0);
    other = second.ed25519PubRaw();
    otherKey = second.ed25519().getPrivate();
  }

  @Test
  void onePageHoldsACommitmentAndThreeEndorsements() {
    List<KarmaChainEntry> chain = chain(owner, key, 1, 3);

    List<byte[]> pages = KarmaChainPages.paginate(chain);

    assertEquals(1, pages.size());
    assertTrue(pages.get(0).length <= KarmaChainPages.MAX_PAGE_BYTES);
    assertEquals(chain, read(pages));
    assertNull(KarmaManifestV1.build(chain, owner), "version 1 fit only one endorsement");
  }

  @Test
  void theHeadVerifiesOnItsOwnAndStartsAtACommitment() {
    List<KarmaChainEntry> chain = chain(owner, key, 12, 2);

    List<byte[]> pages = KarmaChainPages.paginate(chain);
    List<KarmaChainEntry> head = KarmaChainPages.read(pages.get(0), owner, n -> null, null);

    assertTrue(pages.size() > 1);
    assertNotNull(head);
    assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, head.get(0).kind());
    assertEquals(chain.subList(chain.size() - head.size(), chain.size()), head);
  }

  @Test
  void olderPagesExtendTheHeadBackToTheGenesis() {
    List<KarmaChainEntry> chain = chain(owner, key, 6, 2);

    List<byte[]> pages = KarmaChainPages.paginate(chain);

    assertTrue(pages.size() > 2, "test needs several pages, got " + pages.size());
    assertTrue(pages.size() <= 1 + KarmaChainPages.MAX_OLDER_PAGES);
    for (byte[] page : pages) {
      assertTrue(page.length <= KarmaChainPages.MAX_PAGE_BYTES);
    }
    assertEquals(chain, read(pages), "the whole chain, verified from the genesis");
  }

  @Test
  void onlyTheNewestPagesArePublished() {
    List<KarmaChainEntry> chain = chain(owner, key, 30, 3);

    List<byte[]> pages = KarmaChainPages.paginate(chain);
    List<KarmaChainEntry> read = read(pages);

    assertEquals(1 + KarmaChainPages.MAX_OLDER_PAGES, pages.size());
    assertNotNull(read);
    assertTrue(read.get(0).seq() > 0);
    assertEquals(chain.subList(chain.size() - read.size(), chain.size()), read);
  }

  @Test
  void aPageThatDoesNotLinkEndsTheWalk() {
    List<KarmaChainEntry> chain = chain(owner, key, 12, 2);
    List<byte[]> pages = KarmaChainPages.paginate(chain);
    List<byte[]> strangers = KarmaChainPages.paginate(chain(other, otherKey, 12, 2));
    List<KarmaChainEntry> headOnly = KarmaChainPages.read(pages.get(0), owner, n -> null, null);

    List<KarmaChainEntry> read =
        KarmaChainPages.read(pages.get(0), owner, n -> strangers.get(n), null);

    assertEquals(headOnly, read);
  }

  @Test
  void olderPagesThatDoNotVerifyFallBackToTheHead() {
    List<KarmaChainEntry> chain = chain(owner, key, 12, 2);
    List<byte[]> pages = KarmaChainPages.paginate(chain);
    // The signature isn't in the entry hash, so a forged one still links.
    byte[] bad = edit(pages.get(1), page -> lastEntry(page).put("s", new byte[64]));

    List<KarmaChainEntry> read =
        KarmaChainPages.read(pages.get(0), owner, n -> n == 1 ? bad : null, null);

    assertEquals(KarmaChainPages.read(pages.get(0), owner, n -> null, null), read);
  }

  @Test
  void energyIsCarriedAsSigned() {
    // %.3f then %.2f would give 5.01 and break the signature over "5.00".
    KarmaChainEntry ec =
        KarmaChainEntry.createEpochCommitment(
            KarmaChainEntry.GENESIS_PREV_HASH, 0, owner, block(EPOCH, 0), 5.0046, key);

    assertEquals(List.of(ec), read(KarmaChainPages.paginate(List.of(ec))));
  }

  @Test
  void aVersion1ManifestStillReads() {
    List<KarmaChainEntry> chain = new ArrayList<>();
    KarmaChainEntry ec =
        KarmaChainEntry.createEpochCommitment(
            KarmaChainEntry.GENESIS_PREV_HASH, 0, owner, block(EPOCH, 0), 5.0, key);
    chain.add(ec);
    chain.add(endorsement(ec, owner, key, 1, block(EPOCH, 1), 1));

    byte[] v1 = KarmaManifestV1.build(chain, owner).bencode();

    assertEquals(chain, KarmaChainPages.read(v1, owner, n -> null, null));
  }

  @Test
  void itemsFetchedUnderAnotherKeyAreRefused() {
    List<byte[]> pages = KarmaChainPages.paginate(chain(owner, key, 1, 1));

    assertNull(KarmaChainPages.read(pages.get(0), other, n -> null, null));
  }

  @Test
  void aHeadThatMisdescribesItsLastEntryIsRefused() {
    byte[] head = KarmaChainPages.paginate(chain(owner, key, 1, 1)).get(0);

    assertNull(readHead(edit(head, page -> page.put("len", 3L))));
    assertNull(readHead(edit(head, page -> page.put("head", new byte[32]))));
    assertNull(readHead(edit(head, page -> page.remove("len"))));
    assertNull(readHead(edit(head, page -> page.put("pg", -1L))));
  }

  @Test
  void malformedEntriesAreRefused() {
    byte[] head = KarmaChainPages.paginate(chain(owner, key, 1, 1)).get(0);

    assertNull(readHead(edit(head, page -> firstEntry(page).put("en", "NaN"))));
    assertNull(readHead(edit(head, page -> firstEntry(page).put("en", "11.00"))));
    assertNull(readHead(edit(head, page -> firstEntry(page).put("fv", 3L))));
    assertNull(readHead(edit(head, page -> firstEntry(page).put("bk", new byte[31]))));
    assertNull(readHead(edit(head, page -> firstEntry(page).put("k", "XX"))));
    assertNull(readHead(edit(head, page -> lastEntry(page).put("fv", 2L))));
    assertNull(readHead(edit(head, page -> lastEntry(page).put("ih", new byte[19]))));
    assertNull(readHead(edit(head, page -> lastEntry(page).put("sd", 1L << 40))));
    assertNull(readHead(edit(head, page -> page.put("ph", new byte[31]))));
    assertNull(readHead(edit(head, page -> page.put("e", new ArrayList<>()))));
  }

  @Test
  void entriesBeforeTheKarmaChainExistedAreRefused() {
    long early = KarmaConstants.epochForHeight(KarmaConstants.FIRST_KARMA_BLOCK_HEIGHT) - 1;
    KarmaChainEntry ec =
        KarmaChainEntry.createEpochCommitment(
            KarmaChainEntry.GENESIS_PREV_HASH, 0, owner, block(early, 0), 5.0, key);

    assertNull(read(KarmaChainPages.paginate(List.of(ec))));
  }

  @Test
  void saltsNameTheHeadAndTheOlderPages() {
    assertEquals("frostwire-karma-v1", new String(KarmaChainPages.salt(0), StandardCharsets.UTF_8));
    assertEquals(
        "frostwire-karma-v1/3", new String(KarmaChainPages.salt(3), StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> KarmaChainPages.salt(-1));
  }

  @Test
  void emptyChainsHaveNoPages() {
    assertTrue(KarmaChainPages.paginate(List.of()).isEmpty());
    assertTrue(KarmaChainPages.paginate(null).isEmpty());
  }

  // --- helpers ---

  private static List<KarmaChainEntry> read(List<byte[]> pages) {
    return KarmaChainPages.read(
        pages.get(0), owner, n -> n < pages.size() ? pages.get(n) : null, null);
  }

  private static List<KarmaChainEntry> readHead(byte[] head) {
    return KarmaChainPages.read(head, owner, n -> null, null);
  }

  /** {@code epochs} consecutive epochs, each a commitment and {@code endorsements} endorsements. */
  static List<KarmaChainEntry> chain(byte[] pub, PrivateKey signer, int epochs, int endorsements) {
    KarmaChain chain = new KarmaChain(pub);
    int torrent = 0;
    for (int e = 0; e < epochs; e++) {
      chain.commitEpoch(block(EPOCH + e, 0), signer);
      for (int i = 0; i < endorsements; i++) {
        byte[] peer = new byte[32];
        peer[0] = (byte) (i + 1);
        byte[] infoHash = new byte[20];
        infoHash[0] = (byte) torrent;
        infoHash[1] = (byte) (torrent++ >> 8);
        chain.endorse(peer, infoHash, block(EPOCH + e, i + 1), signer);
      }
    }
    return chain.entries();
  }

  private static KarmaChainEntry endorsement(
      KarmaChainEntry prev,
      byte[] pub,
      PrivateKey signer,
      long seq,
      BitcoinBlockReference block,
      int peerSeed) {
    byte[] peer = new byte[32];
    peer[0] = (byte) peerSeed;
    return KarmaChainEntry.createEndorsement(
        prev.entryHash(), seq, pub, block, peer, new byte[20], 1, signer);
  }

  static BitcoinBlockReference block(long epoch, int offset) {
    long height = epoch * KarmaConstants.BLOCKS_PER_EPOCH + offset;
    byte[] hash = new byte[32];
    hash[0] = (byte) height;
    hash[1] = (byte) (height >> 8);
    hash[2] = (byte) (height >> 16);
    return new BitcoinBlockReference(height, hash);
  }

  private static byte[] edit(byte[] page, Consumer<Map<String, Object>> change) {
    Map<String, Object> dict = Bencode.decodeDictionary(page);
    change.accept(dict);
    return Bencode.encode(dict);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstEntry(Map<String, Object> page) {
    return (Map<String, Object>) ((List<Object>) page.get("e")).get(0);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> lastEntry(Map<String, Object> page) {
    List<Object> entries = (List<Object>) page.get("e");
    return (Map<String, Object>) entries.get(entries.size() - 1);
  }
}
