/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory representation of a node's karma chain with energy tracking
 * and budget enforcement.
 *
 * <p>Energy model: each epoch grants {@link KarmaConstants#KARMA_ENERGY_PER_EPOCH}
 * fresh endorsement credits. Unused energy decays by
 * {@link KarmaConstants#ENERGY_DECAY_FACTOR} each epoch. The maximum
 * accumulable energy converges to {@link KarmaConstants#MAX_ENERGY}.
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
     * Load a chain from a list of previously persisted/verified entries.
     * Returns null if the entries do not form a valid chain. On success,
     * the returned chain has its epoch/energy state reconstructed so
     * new endorsements can be appended immediately.
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
     * Start a new epoch. Verifies that the block belongs to a new
     * epoch (greater than the current). Computes energy with decay
     * from the previous epoch's unused energy. Appends an
     * EPOCH_COMMITMENT entry to the chain. Returns the appended entry.
     */
    public synchronized KarmaChainEntry commitEpoch(
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

        // Compute new energy: KARMA_ENERGY_PER_EPOCH + (previousRemaining * decay)
        double previousRemaining = currentEnergy - endorsementsThisEpoch;
        if (previousRemaining < 0) previousRemaining = 0;
        currentEnergy = KarmaConstants.energyAtEpoch(previousRemaining);
        currentEpoch = newEpoch;
        endorsementsThisEpoch = 0;

        KarmaChainEntry entry = KarmaChainEntry.createEpochCommitment(
                headHash(), nextSeq(), ownerPub, block, currentEnergy, signingKey);
        entries.add(entry);
        return entry;
    }

    /**
     * Endorse a peer for a completed download. Verifies that:
     * <ul>
     *   <li>An epoch commitment exists for the current epoch</li>
     *   <li>Energy budget has not been exhausted</li>
     * </ul>
     * Appends an ENDORSEMENT entry to the chain. Returns the appended entry.
     */
    public synchronized KarmaChainEntry endorse(
            byte[] peerPub, byte[] infoHash,
            BitcoinBlockReference currentBlock,
            PrivateKey signingKey) {
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
        if (currentEpoch < 0) {
            throw new IllegalStateException("No epoch committed yet; call commitEpoch first");
        }
        if (currentBlock.epoch() < currentEpoch) {
            throw new IllegalStateException(
                    "Block is from a past epoch (" + currentBlock.epoch() +
                    " < " + currentEpoch + ")");
        }
        if (endorsementsThisEpoch >= Math.floor(currentEnergy)) {
            throw new IllegalStateException(
                    "Energy exhausted for epoch " + currentEpoch +
                    " (used " + endorsementsThisEpoch + " of " +
                    Math.floor(currentEnergy) + ")");
        }

        KarmaChainEntry entry = KarmaChainEntry.createEndorsement(
                headHash(), nextSeq(), ownerPub, currentBlock,
                peerPub, infoHash, 1, signingKey);
        entries.add(entry);
        endorsementsThisEpoch++;
        return entry;
    }

    /** Returns the available integer energy credits in the current epoch. */
    public synchronized int availableEnergy() {
        if (currentEpoch < 0) return 0;
        return (int) Math.floor(currentEnergy) - endorsementsThisEpoch;
    }

    public synchronized long currentEpoch() {
        return currentEpoch;
    }

    /**
     * Validates the entire chain: hash links, signatures, epoch ordering,
     * energy budgets, and monotonic block heights. Returns true if valid.
     * Used when loading a peer's published chain.
     */
    public static boolean verify(List<KarmaChainEntry> chainEntries) {
        if (chainEntries == null || chainEntries.isEmpty()) {
            return false;
        }

        // First entry must have GENESIS_PREV_HASH
        if (!java.util.Arrays.equals(chainEntries.get(0).prevHash(),
                KarmaChainEntry.GENESIS_PREV_HASH)) {
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

        // Epoch commitments must be in monotonically increasing order
        long lastEpoch = -1;
        for (KarmaChainEntry e : chainEntries) {
            if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
                Long ep = e.epoch();
                if (ep != null && ep <= lastEpoch) {
                    return false;
                }
                if (ep != null) lastEpoch = ep;
            }
        }

        // Endorsements must follow an epoch commitment in the chain.
        // An endorsement can be in any epoch >= the most recent commitment's epoch.
        // The energy budget is tracked per-epoch based on the most recent commitment.
        long lastCommitmentEpoch = -1;
        int endorsementsInCurrentEpoch = 0;
        double energyAtCurrentEpoch = 0;
        for (KarmaChainEntry e : chainEntries) {
            if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
                lastCommitmentEpoch = e.epoch();
                endorsementsInCurrentEpoch = 0;
                energyAtCurrentEpoch = e.energy();
            } else if (e.kind() == KarmaChainEntry.Kind.ENDORSEMENT) {
                if (lastCommitmentEpoch < 0) {
                    return false; // Endorsement before any epoch commitment
                }
                long entryEpoch = e.blockHeight() / KarmaConstants.BLOCKS_PER_EPOCH;
                if (entryEpoch < lastCommitmentEpoch) {
                    return false; // Endorsement block before the committed epoch
                }
                endorsementsInCurrentEpoch++;
                if (endorsementsInCurrentEpoch > Math.floor(energyAtCurrentEpoch)) {
                    return false; // Over budget
                }
            }
        }

        return true;
    }
}
