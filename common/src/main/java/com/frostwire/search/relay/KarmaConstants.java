/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Constants for the Bitcoin-anchored peer karma system.
 *
 * <p>Epochs are defined by Bitcoin block height, not wall clock time, so no peer can fake time
 * advancement. Each epoch grants a fixed amount of karma energy; unused energy decays exponentially
 * across epochs to prevent hoarding.
 */
public final class KarmaConstants {

  /**
   * Bitcoin blocks per karma epoch. 144 blocks at ~10 min/block is approximately 24 hours. This is
   * the cadence at which new karma energy becomes available.
   */
  public static final int BLOCKS_PER_EPOCH = 144;

  /**
   * Endorsement points granted per epoch. A peer can issue at most this many endorsements per epoch
   * from fresh energy. Carried-over energy from previous epochs (after decay) adds to the budget.
   */
  public static final int KARMA_ENERGY_PER_EPOCH = 5;

  /**
   * Decay factor applied to unused energy each epoch. With 0.5, half the unused energy from the
   * previous epoch carries forward.
   *
   * <p>Steady-state maximum energy (if never used): {@code KARMA_ENERGY_PER_EPOCH / (1 -
   * DECAY_FACTOR)} = 10.
   */
  public static final double ENERGY_DECAY_FACTOR = 0.5;

  /**
   * Maximum possible energy a node can accumulate (geometric series convergence). This is a
   * convenience constant for validation: any claimed energy above this is fraudulent.
   */
  public static final double MAX_ENERGY = KARMA_ENERGY_PER_EPOCH / (1.0 - ENERGY_DECAY_FACTOR);

  /**
   * Minimum number of leading zero bits required in the node ID ({@code SHA-1(ed25519_pub)}). This
   * is the proof-of-work cost for identity creation. 20 bits ≈ ~1 million SHA-1 attempts ≈ 2-3
   * seconds on modern hardware, but making 10,000 Sybil identities takes hours.
   */
  public static final int IDENTITY_DIFFICULTY = 20;

  /** BEP 46 salt for publishing karma chain entries to DHT. */
  public static final String BEP46_SALT_KARMA = "frostwire-karma-v1";

  /**
   * Tolerance in blocks for epoch boundary verification. A peer's epoch commitment can reference a
   * block within ±EPOCH_TOLERANCE blocks of the epoch boundary to account for propagation delay.
   */
  public static final int EPOCH_TOLERANCE_BLOCKS = 2;

  /**
   * How many blocks must be mined on top of a block before this node anchors a karma entry to it. A
   * block this deep is not replaced by a chain reorganization in practice (six is Bitcoin's
   * customary threshold), so a published entry keeps naming a block that is in the chain, and
   * {@link KarmaChain#verify(java.util.List, BlockHeaderSource)} keeps accepting it. The writer
   * anchors at {@code tip - ANCHOR_DEPTH_BLOCKS}; verifiers accept any block up to the tip, so
   * chains anchored at the tip by earlier versions still verify.
   */
  public static final int ANCHOR_DEPTH_BLOCKS = 6;

  /**
   * Lowest Bitcoin height a remote karma chain's entries may name: the first block of epoch 6610,
   * which holds block 951,939 (mined 2026-06-01), ten days before the karma chain code was written
   * (2026-06-11). No FrostWire client can have signed an entry at a lower height, so a chain that
   * starts below it was made to look older than the feature.
   */
  public static final long FIRST_KARMA_BLOCK_HEIGHT = 6610L * BLOCKS_PER_EPOCH;

  private KarmaConstants() {}

  /** Compute the karma epoch number for a given Bitcoin block height. */
  public static long epochForHeight(long blockHeight) {
    return blockHeight / BLOCKS_PER_EPOCH;
  }

  /**
   * Compute the available energy at an epoch given the energy remaining from the previous epoch.
   * This is the rule of format 1 chains ({@link KarmaChainEntry#FORMAT_DECAY_PER_COMMITMENT}): the
   * carried energy decays once per commitment, however many epochs were skipped.
   */
  public static double energyAtEpoch(double previousRemaining) {
    return KARMA_ENERGY_PER_EPOCH + previousRemaining * ENERGY_DECAY_FACTOR;
  }

  /**
   * The energy of a commitment {@code epochsElapsed} epochs after the previous one, which left
   * {@code previousRemaining} unused: the carried energy decays by {@link #ENERGY_DECAY_FACTOR} for
   * every epoch that passed, committed or not. This is the rule of format 2 chains ({@link
   * KarmaChainEntry#FORMAT_DECAY_PER_EPOCH}), so skipping epochs never keeps more energy than
   * committing each of them would have.
   *
   * @throws IllegalArgumentException if {@code epochsElapsed} is below 1
   */
  public static double energyAtEpoch(double previousRemaining, long epochsElapsed) {
    if (epochsElapsed < 1) {
      throw new IllegalArgumentException("epochsElapsed must be >= 1");
    }
    // Past ~1100 epochs the factor underflows to 0, which is the right limit.
    return KARMA_ENERGY_PER_EPOCH
        + previousRemaining * Math.pow(ENERGY_DECAY_FACTOR, (double) epochsElapsed);
  }
}
