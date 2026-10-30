/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Bencode;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * In-memory representation of a node's karma chain with energy tracking and budget enforcement.
 *
 * <p>Energy model: each epoch grants {@link KarmaConstants#KARMA_ENERGY_PER_EPOCH} fresh
 * endorsement credits. Unused energy decays by {@link KarmaConstants#ENERGY_DECAY_FACTOR} each
 * epoch. The maximum accumulable energy converges to {@link KarmaConstants#MAX_ENERGY}.
 *
 * <p>Thread-safe: all mutating methods are synchronized.
 */
public final class KarmaChain {

  private static final Logger LOG = Logger.getLogger(KarmaChain.class);

  private final byte[] ownerPub;
  private final List<KarmaChainEntry> entries = new ArrayList<>();
  private long currentEpoch = -1;
  private double currentEnergy = 0.0;
  private int endorsementsThisEpoch = 0;

  public KarmaChain(byte[] ownerPub) {
    if (ownerPub == null || ownerPub.length != 32) {
      throw new IllegalArgumentException("ownerPub must be 32 bytes");
    }
    this.ownerPub = ownerPub.clone();
  }

  /**
   * Load a chain from a list of previously persisted/verified entries. Returns null if the entries
   * do not form a valid chain. On success, the returned chain has its epoch/energy state
   * reconstructed so new endorsements can be appended immediately.
   */
  public static KarmaChain load(byte[] ownerPub, List<KarmaChainEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return null;
    }
    if (!verify(entries)) {
      return null;
    }
    KarmaChain chain = new KarmaChain(ownerPub);
    chain.entries.addAll(entries);
    chain.recomputeState();
    return chain;
  }

  private void recomputeState() {
    currentEpoch = -1;
    currentEnergy = 0.0;
    endorsementsThisEpoch = 0;
    for (KarmaChainEntry e : entries) {
      if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        if (e.epoch() != null) {
          currentEpoch = e.epoch();
        }
        if (e.energy() != null) {
          currentEnergy = e.energy();
        }
        endorsementsThisEpoch = 0;
      } else if (e.kind() == KarmaChainEntry.Kind.ENDORSEMENT) {
        endorsementsThisEpoch++;
      }
    }
  }

  /** Read-only view of all entries (in order). */
  public synchronized List<KarmaChainEntry> entries() {
    return Collections.unmodifiableList(new ArrayList<>(entries));
  }

  /** Returns the head entry, or null if the chain is empty. */
  public synchronized KarmaChainEntry head() {
    return entries.isEmpty() ? null : entries.get(entries.size() - 1);
  }

  /** Returns the hash that the next entry's prevHash must reference. */
  public synchronized byte[] headHash() {
    if (entries.isEmpty()) {
      return KarmaChainEntry.GENESIS_PREV_HASH.clone();
    }
    return entries.get(entries.size() - 1).entryHash();
  }

  /** Returns the next sequence number (0 for empty chain). */
  public synchronized long nextSeq() {
    return entries.size();
  }

  /**
   * Start a new epoch. Verifies that the block belongs to a new epoch (greater than the current).
   * Computes energy with decay from the previous epoch's unused energy. Appends an EPOCH_COMMITMENT
   * entry to the chain. Returns the appended entry.
   */
  public synchronized KarmaChainEntry commitEpoch(
      BitcoinBlockReference block, PrivateKey signingKey) {
    KarmaChainEntry entry = prepareEpochCommitment(block, signingKey);
    accept(entry);
    return entry;
  }

  /**
   * Signs the {@code EPOCH_COMMITMENT} that {@link #commitEpoch} would append, without changing the
   * chain. A caller that must persist the entry first stores it, then passes it to {@link #accept},
   * so a failed write leaves the chain as it was.
   */
  synchronized KarmaChainEntry prepareEpochCommitment(
      BitcoinBlockReference block, PrivateKey signingKey) {
    if (block == null) {
      throw new IllegalArgumentException("block is null");
    }
    if (signingKey == null) {
      throw new IllegalArgumentException("signingKey is null");
    }
    long newEpoch = block.epoch();
    if (newEpoch <= currentEpoch) {
      throw new IllegalStateException(
          "Cannot commit epoch " + newEpoch + " (current is " + currentEpoch + ")");
    }
    requireNotBelowHead(block);

    // Compute new energy: KARMA_ENERGY_PER_EPOCH + (previousRemaining * decay)
    double previousRemaining = currentEnergy - endorsementsThisEpoch;
    if (previousRemaining < 0) previousRemaining = 0;
    double energy = KarmaConstants.energyAtEpoch(previousRemaining);
    return KarmaChainEntry.createEpochCommitment(
        headHash(), nextSeq(), ownerPub, block, energy, signingKey);
  }

  /**
   * Endorse a peer for a completed download. Verifies that:
   *
   * <ul>
   *   <li>An epoch commitment exists for the current epoch
   *   <li>Energy budget has not been exhausted
   * </ul>
   *
   * Appends an ENDORSEMENT entry to the chain. Returns the appended entry.
   */
  public synchronized KarmaChainEntry endorse(
      byte[] peerPub, byte[] infoHash, BitcoinBlockReference currentBlock, PrivateKey signingKey) {
    KarmaChainEntry entry = prepareEndorsement(peerPub, infoHash, currentBlock, signingKey);
    accept(entry);
    return entry;
  }

  /**
   * Signs the {@code ENDORSEMENT} that {@link #endorse} would append, after the same checks,
   * without changing the chain; see {@link #prepareEpochCommitment}.
   */
  synchronized KarmaChainEntry prepareEndorsement(
      byte[] peerPub, byte[] infoHash, BitcoinBlockReference currentBlock, PrivateKey signingKey) {
    if (peerPub == null) {
      throw new IllegalArgumentException("peerPub is null");
    }
    if (infoHash == null) {
      throw new IllegalArgumentException("infoHash is null");
    }
    if (currentBlock == null) {
      throw new IllegalArgumentException("currentBlock is null");
    }
    if (signingKey == null) {
      throw new IllegalArgumentException("signingKey is null");
    }
    if (Arrays.equals(peerPub, ownerPub)) {
      throw new IllegalStateException("A chain cannot endorse its own owner");
    }
    if (hasEndorsement(peerPub, infoHash)) {
      throw new IllegalStateException("This source peer was already endorsed for this torrent");
    }
    if (currentEpoch < 0) {
      throw new IllegalStateException("No epoch committed yet; call commitEpoch first");
    }
    if (currentBlock.epoch() < currentEpoch) {
      throw new IllegalStateException(
          "Block is from a past epoch (" + currentBlock.epoch() + " < " + currentEpoch + ")");
    }
    requireNotBelowHead(currentBlock);
    if (endorsementsThisEpoch >= Math.floor(currentEnergy)) {
      throw new IllegalStateException(
          "Energy exhausted for epoch "
              + currentEpoch
              + " (used "
              + endorsementsThisEpoch
              + " of "
              + Math.floor(currentEnergy)
              + ")");
    }

    return KarmaChainEntry.createEndorsement(
        headHash(), nextSeq(), ownerPub, currentBlock, peerPub, infoHash, 1, signingKey);
  }

  /**
   * {@link #verify} rejects a chain whose block heights go down, so an entry is never signed at a
   * block below the head's. The caller's Bitcoin tip can lag behind the block the head was made at
   * (another API, or another thread that appended first).
   */
  private void requireNotBelowHead(BitcoinBlockReference block) {
    if (!entries.isEmpty()) {
      long headHeight = entries.get(entries.size() - 1).blockHeight();
      if (block.height() < headHeight) {
        throw new IllegalStateException(
            "Block " + block.height() + " is below the chain head's block " + headHeight);
      }
    }
  }

  /**
   * Appends an entry made by {@link #prepareEpochCommitment} or {@link #prepareEndorsement} and
   * advances the epoch and energy state. The entry must extend the current head: if the chain
   * changed since it was prepared, it is refused and the chain is left as it was.
   *
   * @throws IllegalStateException if the entry doesn't link to the current head
   */
  synchronized void accept(KarmaChainEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry is null");
    }
    if (entry.seq() != nextSeq()
        || !Arrays.equals(entry.prevHash(), headHash())
        || !Arrays.equals(entry.endorserPub(), ownerPub)) {
      throw new IllegalStateException("Entry seq=" + entry.seq() + " does not extend this chain");
    }
    entries.add(entry);
    if (entry.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
      currentEpoch = entry.epoch();
      currentEnergy = entry.energy();
      endorsementsThisEpoch = 0;
    } else {
      endorsementsThisEpoch++;
    }
  }

  /** True when this chain already contains an endorsement for this peer and torrent pair. */
  public synchronized boolean hasEndorsement(byte[] peerPub, byte[] infoHash) {
    if (peerPub == null || peerPub.length != 32 || infoHash == null || infoHash.length != 20) {
      return false;
    }
    for (KarmaChainEntry entry : entries) {
      if (entry.kind() == KarmaChainEntry.Kind.ENDORSEMENT
          && java.util.Arrays.equals(peerPub, entry.peerPub())
          && java.util.Arrays.equals(infoHash, entry.infoHash())) {
        return true;
      }
    }
    return false;
  }

  /** Returns the available integer energy credits in the current epoch. */
  public synchronized int availableEnergy() {
    if (currentEpoch < 0) return 0;
    return (int) Math.floor(currentEnergy) - endorsementsThisEpoch;
  }

  public synchronized long currentEpoch() {
    return currentEpoch;
  }

  /** Largest remote manifest {@link #parseManifest(byte[], byte[])} accepts, in bytes. */
  static final int MAX_REMOTE_MANIFEST_BYTES = 64 * 1024;

  /** Most entries {@link #parseManifest(byte[], byte[])} accepts in one manifest. */
  static final int MAX_REMOTE_MANIFEST_ENTRIES = 256;

  /**
   * Parses a peer's karma chain manifest, as {@link KarmaChainPublisher} publishes it, and verifies
   * it: every entry must be signed by {@code owner}, and the entries must pass {@link
   * #verify(List)}, or {@link #verifyTail(List, byte[], long)} against the manifest's {@code base}
   * when it has one.
   *
   * @param manifest the manifest's bencoded bytes (untrusted)
   * @param owner the 32-byte Ed25519 key the manifest was fetched under
   * @return the verified entries (unmodifiable), or null if the manifest is too large, is missing
   *     fields, has the wrong shape, or does not verify
   * @throws IllegalArgumentException if {@code manifest} is not canonical bencode, or a hex or
   *     base64 field in it is malformed
   */
  public static List<KarmaChainEntry> parseManifest(byte[] manifest, byte[] owner) {
    return parseManifest(manifest, owner, null);
  }

  /**
   * As {@link #parseManifest(byte[], byte[])}, also checking the entries' Bitcoin blocks against
   * {@code blocks} when it is not null; see {@link #verify(List, BlockHeaderSource)}.
   */
  public static List<KarmaChainEntry> parseManifest(
      byte[] manifest, byte[] owner, BlockHeaderSource blocks) {
    if (manifest == null || manifest.length > MAX_REMOTE_MANIFEST_BYTES) {
      return null;
    }
    return parseManifest(Bencode.asDictionary(Bencode.decode(manifest)), owner, blocks);
  }

  /**
   * As {@link #parseManifest(byte[], byte[])}, for a manifest already decoded to {@link Bencode}'s
   * neutral form.
   *
   * @throws IllegalArgumentException if a hex or base64 field is malformed
   */
  static List<KarmaChainEntry> parseManifest(Map<String, Object> dict, byte[] owner) {
    return parseManifest(dict, owner, null);
  }

  static List<KarmaChainEntry> parseManifest(
      Map<String, Object> dict, byte[] owner, BlockHeaderSource blocks) {
    if (dict == null) {
      return null;
    }
    List<Object> entryDicts = Bencode.asList(dict.get("entries"));
    if (entryDicts == null || entryDicts.size() > MAX_REMOTE_MANIFEST_ENTRIES) {
      return null;
    }
    List<KarmaChainEntry> chain = new ArrayList<>(entryDicts.size());
    for (Object e : entryDicts) {
      Map<String, Object> entryDict = Bencode.asDictionary(e);
      if (entryDict == null) {
        return null;
      }
      KarmaChainEntry entry = KarmaChainEntry.fromDictionary(entryDict, owner);
      if (entry == null || !Arrays.equals(owner, entry.endorserPub())) {
        return null;
      }
      chain.add(entry);
    }
    Object baseValue = dict.get("base");
    if (baseValue != null) {
      Map<String, Object> baseDict = Bencode.asDictionary(baseValue);
      if (baseDict == null) {
        return null;
      }
      Long baseSeq = Bencode.asLong(baseDict.get("seq"));
      String basePrevHash = Bencode.asString(baseDict.get("ph"));
      if (baseSeq == null || basePrevHash == null) {
        return null;
      }
      if (!verifyTail(chain, Hex.decode(basePrevHash), baseSeq, blocks)) {
        return null;
      }
    } else if (!verify(chain, blocks)) {
      return null;
    }
    return Collections.unmodifiableList(chain);
  }

  /**
   * As {@link #verify(List)}, then checks the Bitcoin blocks the entries name against {@code
   * blocks}, if not null, with an offline fallback; see {@link #checkBlocks}.
   */
  public static boolean verify(List<KarmaChainEntry> chainEntries, BlockHeaderSource blocks) {
    return verify(chainEntries) && checkBlocks(chainEntries, blocks);
  }

  /**
   * Validates the entire chain: genesis link, one owner, signatures, hash links, seq, monotonic
   * block heights, and the epoch and budget rules of {@link #checkEpochsAndBudget}. Offline: block
   * heights and hashes aren't compared with Bitcoin; {@link #verify(List, BlockHeaderSource)} does
   * that. Used when loading our own chain and a peer's published chain.
   */
  public static boolean verify(List<KarmaChainEntry> chainEntries) {
    if (chainEntries == null || chainEntries.isEmpty()) {
      return false;
    }

    // First entry must have GENESIS_PREV_HASH
    if (!java.util.Arrays.equals(
        chainEntries.get(0).prevHash(), KarmaChainEntry.GENESIS_PREV_HASH)) {
      return false;
    }

    // All entries must have the same endorserPub
    byte[] expectedPub = chainEntries.get(0).endorserPub();
    for (KarmaChainEntry e : chainEntries) {
      if (!java.util.Arrays.equals(e.endorserPub(), expectedPub)) {
        return false;
      }
    }

    // Each entry must verify its signature
    for (KarmaChainEntry e : chainEntries) {
      if (!e.verifySignature()) {
        return false;
      }
    }

    // Each entry's prevHash must link to the previous entry's entryHash
    for (int i = 1; i < chainEntries.size(); i++) {
      byte[] expectedPrev = chainEntries.get(i - 1).entryHash();
      if (!java.util.Arrays.equals(chainEntries.get(i).prevHash(), expectedPrev)) {
        return false;
      }
    }

    // Sequence numbers must be 0-based and monotonic
    for (int i = 0; i < chainEntries.size(); i++) {
      if (chainEntries.get(i).seq() != i) {
        return false;
      }
    }

    // Block heights must be monotonically non-decreasing
    for (int i = 1; i < chainEntries.size(); i++) {
      if (chainEntries.get(i).blockHeight() < chainEntries.get(i - 1).blockHeight()) {
        return false;
      }
    }

    return checkEpochsAndBudget(chainEntries, true);
  }

  /**
   * Validates a truncated tail [N..M] against its declared base: the seq of the first included
   * entry and the entryHash of the entry immediately before it (or genesis for a complete chain).
   *
   * <p>Full-chain {@link #verify(List)} is untouched; this method exists so trimmed manifests that
   * cannot start at seq 0 still get explicit verified-tail semantics: hash links within the tail,
   * signatures, seq continuity from {@code baseSeq}, the first entry's prevHash equal to {@code
   * basePrevHash}, and the same monotonicity/budget checks as the full chain. A tail must begin
   * with an epoch commitment so its endorsement budget is independently verifiable without the
   * omitted prefix; that commitment's energy can't be recomputed and is only bounded, unless the
   * tail starts at seq 0, where it is the whole chain and held to the full chain's rules.
   */
  public static boolean verifyTail(List<KarmaChainEntry> tail, byte[] basePrevHash, long baseSeq) {
    if (tail == null || tail.isEmpty()) {
      return false;
    }
    if (basePrevHash == null || basePrevHash.length != 32) {
      return false;
    }
    if (baseSeq < 0) {
      return false;
    }
    if (baseSeq == 0 && !Arrays.equals(basePrevHash, KarmaChainEntry.GENESIS_PREV_HASH)) {
      return false;
    }
    if (tail.get(0).kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
      return false;
    }
    // First entry must anchor to the declared base.
    if (!java.util.Arrays.equals(tail.get(0).prevHash(), basePrevHash)) {
      return false;
    }
    if (tail.get(0).seq() != baseSeq) {
      return false;
    }

    // All entries must share one endorser.
    byte[] expectedPub = tail.get(0).endorserPub();
    for (KarmaChainEntry e : tail) {
      if (!java.util.Arrays.equals(e.endorserPub(), expectedPub)) {
        return false;
      }
    }

    // Signatures.
    for (KarmaChainEntry e : tail) {
      if (!e.verifySignature()) {
        return false;
      }
    }

    // Hash links within the tail.
    for (int i = 1; i < tail.size(); i++) {
      byte[] expectedPrev = tail.get(i - 1).entryHash();
      if (!java.util.Arrays.equals(tail.get(i).prevHash(), expectedPrev)) {
        return false;
      }
    }

    // Seq continuity from baseSeq.
    for (int i = 0; i < tail.size(); i++) {
      if (tail.get(i).seq() != baseSeq + i) {
        return false;
      }
    }

    // Block heights monotonically non-decreasing.
    for (int i = 1; i < tail.size(); i++) {
      if (tail.get(i).blockHeight() < tail.get(i - 1).blockHeight()) {
        return false;
      }
    }

    // A tail from seq 0 is the whole chain: hold it to the full chain's rules.
    return checkEpochsAndBudget(tail, baseSeq == 0);
  }

  /**
   * As {@link #verifyTail(List, byte[], long)}, then checks the Bitcoin blocks against {@code
   * blocks}, if not null; see {@link #checkBlocks}.
   */
  public static boolean verifyTail(
      List<KarmaChainEntry> tail, byte[] basePrevHash, long baseSeq, BlockHeaderSource blocks) {
    return verifyTail(tail, basePrevHash, baseSeq) && checkBlocks(tail, blocks);
  }

  /**
   * Largest difference allowed between a commitment's declared energy and the decay rule: the
   * signed form keeps two decimals ({@code %.2f}), and the writer computes from the unrounded
   * value.
   */
  private static final double ENERGY_TOLERANCE = 0.01 + 1e-9;

  /**
   * The checks that hold a chain to its endorsement budget, shared by {@link #verify(List)} and
   * {@link #verifyTail}:
   *
   * <ul>
   *   <li>a commitment's epoch is its Bitcoin height's ({@code ep == bh / 144}), and epochs
   *       strictly increase, so two commitments can't share an epoch or a height's epoch;
   *   <li>its energy is {@link KarmaConstants#energyAtEpoch} of the previous commitment's unused
   *       energy (one decay step, however many epochs were skipped, as {@link #commitEpoch}
   *       computes it), within {@link #ENERGY_TOLERANCE}; a full chain's first commitment has
   *       nothing carried (5). A tail's first commitment follows an omitted prefix, so its energy
   *       is only bounded by {@link KarmaConstants#MAX_ENERGY};
   *   <li>an endorsement follows a commitment, sits in its epoch or later, names someone other than
   *       the chain's owner, and stays within the floor of the commitment's energy.
   * </ul>
   *
   * Heights and hashes aren't checked against Bitcoin here; see {@link #checkBlocks}.
   */
  private static boolean checkEpochsAndBudget(
      List<KarmaChainEntry> entries, boolean startsAtGenesis) {
    long lastCommitmentEpoch = -1;
    double energy = 0;
    int endorsements = 0;
    boolean energyKnown = startsAtGenesis;
    for (KarmaChainEntry e : entries) {
      if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        Long epoch = e.epoch();
        Double declared = e.energy();
        if (epoch == null
            || declared == null
            || epoch != KarmaConstants.epochForHeight(e.blockHeight())
            || epoch <= lastCommitmentEpoch) {
          return false;
        }
        // NaN fails every comparison, which would lift the budget below (#1401).
        if (!Double.isFinite(declared)
            || declared < 0
            || declared > KarmaConstants.MAX_ENERGY + ENERGY_TOLERANCE) {
          return false;
        }
        if (energyKnown) {
          double expected = KarmaConstants.energyAtEpoch(Math.max(0, energy - endorsements));
          if (Math.abs(declared - expected) > ENERGY_TOLERANCE) {
            return false;
          }
        }
        energyKnown = true;
        lastCommitmentEpoch = epoch;
        energy = declared;
        endorsements = 0;
      } else if (e.kind() == KarmaChainEntry.Kind.ENDORSEMENT) {
        if (lastCommitmentEpoch < 0
            || KarmaConstants.epochForHeight(e.blockHeight()) < lastCommitmentEpoch
            || Arrays.equals(e.peerPub(), e.endorserPub())) {
          return false;
        }
        endorsements++;
        if (endorsements > Math.floor(energy)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Checks the entries' Bitcoin blocks against {@code blocks}: no height beyond the tip plus {@link
   * KarmaConstants#EPOCH_TOLERANCE_BLOCKS}, and each commitment's block hash equal to Bitcoin's at
   * its height. This is what stops a chain from claiming epochs that haven't happened yet, which
   * the structural checks alone can't.
   *
   * <p>Offline fallback: with no source, an unknown tip ({@code -1}), or a height the source can't
   * resolve (or that throws), that check is skipped and the chain is judged by the structural
   * checks only, so verifying never depends on the network. Endorsement block hashes aren't looked
   * up: they don't change the budget, and one lookup per commitment bounds the work per chain.
   */
  private static boolean checkBlocks(List<KarmaChainEntry> entries, BlockHeaderSource blocks) {
    if (blocks == null) {
      return true;
    }
    long tip;
    try {
      tip = blocks.getChainTipHeight();
    } catch (Throwable t) {
      tip = -1;
    }
    if (tip >= 0) {
      for (KarmaChainEntry e : entries) {
        if (e.blockHeight() > tip + KarmaConstants.EPOCH_TOLERANCE_BLOCKS) {
          return false;
        }
      }
    }
    for (KarmaChainEntry e : entries) {
      if (e.kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        continue;
      }
      BitcoinBlockReference real;
      try {
        real = blocks.getBlock(e.blockHeight());
      } catch (Throwable t) {
        real = null;
      }
      if (real != null && !Arrays.equals(real.hash(), e.blockHash())) {
        return false;
      }
    }
    return true;
  }
}
