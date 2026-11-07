/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.util.Hex;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The local karma chain table is keyed by owner (frostwire/frostwire#1416): after an identity
 * change the new identity's chain starts fresh beside the old one instead of replacing its rows seq
 * by seq, and loading a chain never returns another identity's entries.
 */
class KarmaChainTableOwnerKeyTest {

  private static final long H = 960_000L;

  private static IdentityKeys alice;
  private static IdentityKeys bob;

  @TempDir Path dir;

  @BeforeAll
  static void identities() throws Exception {
    alice = IdentityKeys.generate(0);
    bob = IdentityKeys.generate(0);
  }

  @Test
  void twoIdentitiesChainsLiveSideBySide() {
    try (KarmaChainTable table = KarmaChainTable.open(db())) {
      table.append(commitment(alice));
      table.append(commitment(bob));

      assertOwnChain(table, alice);
      assertOwnChain(table, bob);
    }
  }

  @Test
  void aTableKeyedBySeqIsRekeyedByOwnerAndKeepsItsRows() throws Exception {
    File db = db();
    // 7.1.3 wrote format 1 commitments.
    KarmaChainEntry legacy =
        KarmaChainEntry.createEpochCommitment(
            KarmaChainEntry.GENESIS_PREV_HASH,
            0,
            alice.ed25519PubRaw(),
            block(H),
            KarmaConstants.KARMA_ENERGY_PER_EPOCH,
            alice.ed25519().getPrivate());
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath())) {
      try (Statement s = c.createStatement()) {
        s.execute(
            "CREATE TABLE karma_chain (seq INTEGER PRIMARY KEY, kind TEXT NOT NULL, "
                + "prev_hash TEXT NOT NULL, endorser_pub TEXT NOT NULL, timestamp INTEGER NOT"
                + " NULL, block_height INTEGER NOT NULL, block_hash TEXT NOT NULL, epoch INTEGER,"
                + " energy REAL, peer_pub TEXT, info_hash TEXT, score_delta INTEGER, signature"
                + " TEXT NOT NULL, canonical_bytes BLOB NOT NULL)");
      }
      insertAsVersion7_1_3Did(c, legacy);
    }

    try (KarmaChainTable table = KarmaChainTable.open(db)) {
      assertOwnChain(table, alice);
      // Under the old key, bob's seq 0 replaced alice's.
      table.append(commitment(bob));
      assertOwnChain(table, alice);
      assertOwnChain(table, bob);
    }
    try (KarmaChainTable reopened = KarmaChainTable.open(db)) {
      assertOwnChain(reopened, alice);
    }
  }

  @Test
  void aRejectedChainIsSetAsideWithoutTouchingAnotherIdentitysChain() throws Exception {
    File db = db();
    try (KarmaChainTable table = KarmaChainTable.open(db)) {
      table.append(commitment(alice));
      table.append(orphanEndorsement(bob));

      assertTrue(table.loadChainForWriting(bob.ed25519PubRaw()).entries().isEmpty());

      assertOwnChain(table, alice);
    }
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM karma_chain")) {
      assertEquals(1, rs.getInt(1), "only bob's rejected row left the chain table");
    }
  }

  private File db() {
    return dir.resolve("karma.db").toFile();
  }

  private static void assertOwnChain(KarmaChainTable table, IdentityKeys owner) {
    KarmaChain chain = table.loadChain(owner.ed25519PubRaw());
    assertEquals(1, chain.entries().size());
    assertArrayEquals(owner.ed25519PubRaw(), chain.entries().get(0).endorserPub());
  }

  private static KarmaChainEntry commitment(IdentityKeys owner) {
    return new KarmaChain(owner.ed25519PubRaw())
        .commitEpoch(block(H), owner.ed25519().getPrivate());
  }

  private static KarmaChainEntry orphanEndorsement(IdentityKeys owner) {
    byte[] peer = new byte[32];
    peer[0] = 9;
    return KarmaChainEntry.createEndorsement(
        KarmaChainEntry.GENESIS_PREV_HASH,
        0,
        owner.ed25519PubRaw(),
        block(H),
        peer,
        new byte[20],
        1,
        owner.ed25519().getPrivate());
  }

  private static BitcoinBlockReference block(long height) {
    byte[] hash = new byte[32];
    hash[0] = (byte) height;
    return new BitcoinBlockReference(height, hash);
  }

  /** The row FrostWire 7.1.3 wrote for an epoch commitment. */
  private static void insertAsVersion7_1_3Did(Connection c, KarmaChainEntry e) throws Exception {
    try (PreparedStatement ps =
        c.prepareStatement(
            "INSERT INTO karma_chain (seq, kind, prev_hash, endorser_pub, timestamp, block_height,"
                + " block_hash, epoch, energy, signature, canonical_bytes) VALUES"
                + " (?,?,?,?,?,?,?,?,?,?,?)")) {
      Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
      ps.setLong(1, e.seq());
      ps.setString(2, e.kind().code());
      ps.setString(3, Hex.encode(e.prevHash()));
      ps.setString(4, b64.encodeToString(e.endorserPub()));
      ps.setLong(5, e.timestamp());
      ps.setLong(6, e.blockHeight());
      ps.setString(7, Hex.encode(e.blockHash()));
      ps.setLong(8, e.epoch());
      ps.setDouble(9, e.energy());
      ps.setString(10, b64.encodeToString(e.signature()));
      ps.setBytes(11, e.canonicalBytes());
      ps.executeUpdate();
    }
  }
}
