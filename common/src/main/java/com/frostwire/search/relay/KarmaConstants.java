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
 * <p>Epochs are defined by Bitcoin block height, not wall clock time,
 * so no peer can fake time advancement. Each epoch grants a fixed
 * amount of karma energy; unused energy decays exponentially across
 * epochs to prevent hoarding.
 */
public final class KarmaConstants {

    /**
     * Bitcoin blocks per karma epoch. 144 blocks at ~10 min/block
     * is approximately 24 hours. This is the cadence at which new
     * karma energy becomes available.
     */
    public static final int BLOCKS_PER_EPOCH = 144;

    /**
     * Endorsement points granted per epoch. A peer can issue at most
     * this many endorsements per epoch from fresh energy. Carried-over
     * energy from previous epochs (after decay) adds to the budget.
     */
    public static final int KARMA_ENERGY_PER_EPOCH = 5;

    /**
     * Decay factor applied to unused energy each epoch. With 0.5, half
     * the unused energy from the previous epoch carries forward.
     *
     * <p>Steady-state maximum energy (if never used):
     * {@code KARMA_ENERGY_PER_EPOCH / (1 - DECAY_FACTOR)} = 10.
     */
    public static final double ENERGY_DECAY_FACTOR = 0.5;

    /**
     * Maximum possible energy a node can accumulate (geometric series
     * convergence). This is a convenience constant for validation:
     * any claimed energy above this is fraudulent.
     */
    public static final double MAX_ENERGY =
            KARMA_ENERGY_PER_EPOCH / (1.0 - ENERGY_DECAY_FACTOR);

    /**
     * Minimum number of leading zero bits required in the node ID
     * ({@code SHA-1(ed25519_pub)}). This is the proof-of-work cost
     * for identity creation. 20 bits ≈ ~1 million SHA-1 attempts
     * ≈ 2-3 seconds on modern hardware, but making 10,000 Sybil
     * identities takes hours.
     */
    public static final int IDENTITY_DIFFICULTY = 20;

    /**
     * BEP 46 salt for publishing karma chain entries to DHT.
     */
    public static final String BEP46_SALT_KARMA = "frostwire-karma-v1";

    /**
     * Tolerance in blocks for epoch boundary verification. A peer's
     * epoch commitment can reference a block within ±EPOCH_TOLERANCE
     * blocks of the epoch boundary to account for propagation delay.
     */
    public static final int EPOCH_TOLERANCE_BLOCKS = 2;

    private KarmaConstants() {
    }

    /**
     * Compute the karma epoch number for a given Bitcoin block height.
     */
    public static long epochForHeight(long blockHeight) {
        return blockHeight / BLOCKS_PER_EPOCH;
    }

    /**
     * Compute the available energy at an epoch given the energy
     * remaining from the previous epoch.
     */
    public static double energyAtEpoch(double previousRemaining) {
        return KARMA_ENERGY_PER_EPOCH + previousRemaining * ENERGY_DECAY_FACTOR;
    }
}
