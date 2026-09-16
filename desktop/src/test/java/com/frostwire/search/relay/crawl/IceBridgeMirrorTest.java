/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IceBridgeMirrorTest {

  private static final String HASH_A = "0123456789abcdef0123456789abcdef01234567";
  private static final String HASH_B = "fedcba9876543210fedcba9876543210fedcba98";

  @Test
  void allowlistParsingIgnoresCommentsAndBlanks() {
    String text =
        "# operator allowlist\n"
            + "\n"
            + HASH_A
            + "   # inline comment\n"
            + "\t"
            + HASH_B.toUpperCase()
            + "\t\n"
            + "   \n";

    Set<String> hashes = IceBridgeMirror.parseAllowlist(text);

    assertEquals(Set.of(HASH_A, HASH_B), hashes);
  }

  @Test
  void allowlistParsingRejectsInvalidLengthAndNonHex() {
    String text =
        HASH_A
            + "\n"
            + "0123456789abcdef0123456789abcdef0123456\n" // 39 chars
            + "0123456789abcdef0123456789abcdef012345678\n" // 41 chars
            + "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\n"
            + "not-a-hash\n";

    Set<String> hashes = IceBridgeMirror.parseAllowlist(text);

    assertEquals(Set.of(HASH_A), hashes);
  }

  @Test
  void allowArgParsingSkipsBlankAndInvalidEntries() {
    String csv = HASH_A + ", ," + HASH_B.toUpperCase() + ",too-short,";

    Set<String> hashes = IceBridgeMirror.parseAllowArg(csv);

    assertEquals(Set.of(HASH_A, HASH_B), hashes);
  }

  @Test
  void allowlistHelpersAreNullAndBlankSafe() {
    assertTrue(IceBridgeMirror.parseAllowlist(null).isEmpty());
    assertTrue(IceBridgeMirror.parseAllowlist("  \n # only comments\n").isEmpty());
    assertTrue(IceBridgeMirror.parseAllowArg(null).isEmpty());
    assertTrue(IceBridgeMirror.parseAllowArg("  ").isEmpty());
    assertTrue(IceBridgeMirror.isValidInfoHash(HASH_A));
    assertFalse(IceBridgeMirror.isValidInfoHash("abc"));
    assertFalse(IceBridgeMirror.isValidInfoHash(null));
  }

  @Test
  void byteBudgetAdmitsUpToTheCapAndRefusesBeyondIt() {
    IceBridgeMirror.ByteBudget budget = new IceBridgeMirror.ByteBudget(100L);

    assertTrue(budget.canFit(60L));
    assertTrue(budget.tryReserve(60L));
    assertEquals(60L, budget.usedBytes());

    assertFalse(budget.canFit(50L));
    assertFalse(budget.tryReserve(50L));
    assertEquals(60L, budget.usedBytes());

    assertTrue(budget.tryReserve(40L));
    assertEquals(100L, budget.usedBytes());

    assertFalse(budget.canFit(1L));
    assertFalse(budget.tryReserve(1L));
    assertEquals(100L, budget.usedBytes());
  }

  @Test
  void byteBudgetTreatsUnknownSizeAsFreeAndZeroCapAsNoRoom() {
    IceBridgeMirror.ByteBudget zeroCap = new IceBridgeMirror.ByteBudget(0L);
    assertTrue(zeroCap.canFit(0L));
    assertTrue(zeroCap.tryReserve(0L));
    assertFalse(zeroCap.canFit(1L));

    IceBridgeMirror.ByteBudget negative = new IceBridgeMirror.ByteBudget(-5L);
    assertFalse(negative.canFit(1L));
  }

  @Test
  void holderLookupMissIsSkippedRatherThanGuessed() {
    List<CrawlerStore.Torrent> catalog = List.of(torrent(HASH_A, 1024L, "holder-pub-a"));

    assertEquals("holder-pub-a", IceBridgeMirror.holderPubFor(catalog, HASH_A));
    assertNull(IceBridgeMirror.holderPubFor(catalog, HASH_B));
    assertNull(IceBridgeMirror.holderPubFor(catalog, null));
    assertNull(IceBridgeMirror.holderPubFor(null, HASH_A));
  }

  @Test
  void holderLookupTreatsBlankPublisherAsUnknown() {
    List<CrawlerStore.Torrent> catalog =
        List.of(torrent(HASH_A, 1024L, "   "), torrent(HASH_B, 2048L, null));

    assertNull(IceBridgeMirror.holderPubFor(catalog, HASH_A));
    assertNull(IceBridgeMirror.holderPubFor(catalog, HASH_B));
  }

  private static CrawlerStore.Torrent torrent(String infohash, long sizeBytes, String publisher) {
    return new CrawlerStore.Torrent(infohash, "name", sizeBytes, 1, publisher, 1L, 2L, 1);
  }
}
