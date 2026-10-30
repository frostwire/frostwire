/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A persisted karma chain that fails verification starts a fresh chain, and the fresh chain's
 * entries reuse its sequence numbers. They must not overwrite the old rows: the old chain is kept
 * aside, so a verifier bug or a new rule never destroys a user's karma for good. Only the writer,
 * which will append, moves it: a reader such as a settings pane must not change the store while the
 * writer runs.
 */
class KarmaChainTableRejectedChainTest {

  private static IdentityKeys identity;
  private static KeyPair keyPair;
  private static byte[] pubRaw;

  private File tempDir;
  private File dbFile;
  private KarmaChainTable table;

  @BeforeAll
  static void setUpClass() throws Exception {
    identity = IdentityKeys.generate(4);
    keyPair = identity.ed25519();
    pubRaw = identity.ed25519PubRaw();
  }

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("karma-chain-rejected-test-").toFile();
    dbFile = new File(tempDir, "test.db");
    table = KarmaChainTable.open(dbFile);
  }

  @AfterEach
  void tearDown() {
    if (table != null && table.isOpen()) {
      table.close();
    }
    File[] files = tempDir.listFiles();
    if (files != null) {
      for (File f : files) {
        f.delete();
      }
    }
    tempDir.delete();
  }

  @Test
  void aChainThatFailsVerificationIsKeptAside() throws Exception {
    table.append(orphanEndorsement());

    assertTrue(table.loadChainForWriting(pubRaw).entries().isEmpty());

    List<String> rejected = rejectedTables();
    assertEquals(1, rejected.size(), "the rejected chain is kept in its own table");
    assertEquals(1, count(rejected.get(0)));
    assertEquals(0, count(KarmaChainTable.CHAIN_TABLE));
  }

  @Test
  void theFreshChainDoesNotOverwriteTheRejectedOne() throws Exception {
    KarmaChainEntry orphan = orphanEndorsement();
    table.append(orphan);
    KarmaChain fresh = table.loadChainForWriting(pubRaw);

    KarmaChainEntry commitment = fresh.commitEpoch(block(850000L), keyPair.getPrivate());
    table.append(commitment);

    assertEquals(1, count(rejectedTables().get(0)));
    assertEquals(1, count(KarmaChainTable.CHAIN_TABLE));
    assertEquals(1, table.loadChain(pubRaw).entries().size(), "the fresh chain loads");
  }

  @Test
  void aValidChainIsLeftInPlace() throws Exception {
    KarmaChain chain = new KarmaChain(pubRaw);
    table.append(chain.commitEpoch(block(850000L), keyPair.getPrivate()));

    assertEquals(1, table.loadChainForWriting(pubRaw).entries().size());

    assertEquals(List.of(), rejectedTables());
  }

  @Test
  void aReaderLeavesARejectedChainInPlace() throws Exception {
    table.append(orphanEndorsement());

    assertTrue(table.loadChain(pubRaw).entries().isEmpty());

    assertEquals(List.of(), rejectedTables());
    assertEquals(1, count(KarmaChainTable.CHAIN_TABLE));
  }

  @Test
  void theWriterSetsARejectedChainAsideWhenItStarts() throws Exception {
    table.append(orphanEndorsement());

    new KarmaChainWriter(identity, new FixedTip(850100L), table);

    assertEquals(1, rejectedTables().size());
    assertEquals(0, count(KarmaChainTable.CHAIN_TABLE));
  }

  private static KarmaChainEntry orphanEndorsement() {
    // An endorsement with no epoch commitment before it fails verification.
    byte[] peer = new byte[32];
    peer[0] = 9;
    byte[] infoHash = new byte[20];
    infoHash[0] = 9;
    return KarmaChainEntry.createEndorsement(
        KarmaChainEntry.GENESIS_PREV_HASH,
        0,
        pubRaw,
        block(850050L),
        peer,
        infoHash,
        1,
        keyPair.getPrivate());
  }

  private List<String> rejectedTables() throws Exception {
    List<String> names = new ArrayList<>();
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
                    + " AND name LIKE 'karma_chain_rejected_%'")) {
      while (rs.next()) {
        names.add(rs.getString(1));
      }
    }
    return names;
  }

  private long count(String tableName) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private static final class FixedTip implements BlockHeaderSource {
    private final long tip;

    FixedTip(long tip) {
      this.tip = tip;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      return block(height);
    }

    @Override
    public long getChainTipHeight() {
      return tip;
    }
  }

  private static BitcoinBlockReference block(long height) {
    byte[] hash = new byte[32];
    for (int i = 0; i < 32; i++) {
      hash[i] = (byte) ((height + i) & 0xff);
    }
    return new BitcoinBlockReference(height, hash);
  }
}
