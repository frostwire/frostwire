/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.security.PrivateKey;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Bridges the in-memory {@link KarmaChain} to download-completion events
 * and Bitcoin-anchored persistence.
 *
 * <p>On each {@link KarmaEndorsementSink#onDownloadCompletedFromPeer},
 * the writer:
 * <ol>
 *   <li>Resolves a current Bitcoin block via the injected
 *       {@link BlockHeaderSource}.</li>
 *   <li>Lazily commits an {@code EPOCH_COMMITMENT} entry if the chain
 *       has not yet committed for this epoch.</li>
 *   <li>Appends an {@code ENDORSEMENT} entry and persists both new
 *       entries to the {@link KarmaChainStore}.</li>
 * </ol>
 *
 * <p>Thread-safety: chain/store mutations are serialized through a
 * {@link ReentrantLock}; provider reads run outside that lock. The in-memory
 * {@link KarmaChain} already
 * synchronizes internally; the additional lock prevents a download
 * completion and a periodic epoch commit from racing.
 *
 * <p>Fail-closed: any error resolving a Bitcoin block, signing, or
 * persisting is logged and swallowed. The download itself is never
 * affected by karma bookkeeping failures.
 *
 * <p>The chain is restored through {@link KarmaChainStore#loadChain} on construction.
 * Construct and invoke on workers; the store and block source are borrowed.
 */
public final class KarmaChainWriter implements KarmaEndorsementSink, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(KarmaChainWriter.class);

    private final byte[] ownerPub;
    private final PrivateKey signingKey;
    private final BlockHeaderSource blockSource;
    private final KarmaChainStore table;
    private final KarmaChain chain;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final DhtAdvertiser.Lifecycle lifecycle;

    public KarmaChainWriter(IdentityKeys identity,
                            BlockHeaderSource blockSource,
                            KarmaChainStore table) {
        this(identity, blockSource, table, () -> true);
    }

    /** The nonblocking predicate permanently revokes this writer when its identity is replaced. */
    public KarmaChainWriter(IdentityKeys identity,
                            BlockHeaderSource blockSource,
                            KarmaChainStore table,
                            BooleanSupplier permitted) {
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        if (blockSource == null) {
            throw new IllegalArgumentException("blockSource is null");
        }
        if (table == null) {
            throw new IllegalArgumentException("table is null");
        }
        this.ownerPub = identity.ed25519PubRaw();
        this.signingKey = identity.ed25519().getPrivate();
        this.blockSource = blockSource;
        this.table = table;
        this.lifecycle = new DhtAdvertiser.Lifecycle(permitted);
        this.chain = table.loadChain(ownerPub);
    }

    /**
     * Read-only view of the in-memory chain. Used by tests and any
     * periodic publisher task that needs the current head.
     */
    public KarmaChain chain() {
        return chain;
    }

    /**
     * Lazily commit an epoch if the chain has not yet committed for
     * the current Bitcoin epoch. No-op if already up to date or if
     * the block source cannot resolve a tip.
     */
    public void commitEpochIfNeeded() {
        commitEpochIfNeeded(() -> true);
    }

    void commitEpochIfNeeded(BooleanSupplier permitted) {
        if (!lifecycle.enter()) {
            return;
        }
        try {
            if (!isPermitted(permitted)) {
                return;
            }
            long tip = blockSource.getChainTipHeight();
            if (tip < 0 || !isPermitted(permitted)) {
                return;
            }
            BitcoinBlockReference block = blockSource.getBlock(tip);
            if (block == null || !isPermitted(permitted)) {
                return;
            }
            writeLock.lock();
            try {
                if (isPermitted(permitted) && block.epoch() > chain.currentEpoch()) {
                    appendCommitment(block);
                }
            } finally {
                writeLock.unlock();
            }
        } catch (Throwable t) {
            LOG.warn("KarmaChainWriter.commitEpochIfNeeded failed", t);
        } finally {
            lifecycle.leave();
        }
    }

    @Override
    public void onDownloadCompletedFromPeer(byte[] peerEd25519Pub, byte[] infoHash) {
        onDownloadCompletedFromPeer(peerEd25519Pub, infoHash, () -> true);
    }

    void onDownloadCompletedFromPeer(byte[] peerEd25519Pub, byte[] infoHash,
                                     BooleanSupplier permitted) {
        if (peerEd25519Pub == null) {
            return;
        }
        if (infoHash == null) {
            return;
        }
        if (!lifecycle.enter()) {
            return;
        }
        try {
            peerEd25519Pub = peerEd25519Pub.clone();
            infoHash = infoHash.clone();
            if (!isPermitted(permitted)) {
                return;
            }
            long tip = blockSource.getChainTipHeight();
            if (tip < 0 || !isPermitted(permitted)) {
                LOG.debug("Skipping endorsement: no Bitcoin chain tip available");
                return;
            }
            BitcoinBlockReference block = blockSource.getBlock(tip);
            if (block == null || !isPermitted(permitted)) {
                LOG.debug("Skipping endorsement: could not resolve tip block " + tip);
                return;
            }
            writeLock.lock();
            try {
                if (!isPermitted(permitted)) {
                    return;
                }
                if (block.epoch() > chain.currentEpoch()) {
                    appendCommitment(block);
                }
                if (!isPermitted(permitted) || chain.availableEnergy() <= 0) {
                    return;
                }
                KarmaChainEntry endorsement = chain.endorse(
                        peerEd25519Pub, infoHash, block, signingKey);
                table.append(endorsement);
            } catch (IllegalStateException e) {
                LOG.debug("Endorsement rejected by chain: " + e.getMessage());
            } finally {
                writeLock.unlock();
            }
        } catch (Throwable t) {
            LOG.warn("KarmaChainWriter.onDownloadCompletedFromPeer failed", t);
        } finally {
            lifecycle.leave();
        }
    }

    private boolean isPermitted(BooleanSupplier permitted) {
        return lifecycle.getAsBoolean() && permitted.getAsBoolean() && !lifecycle.isClosed();
    }

    /** Non-waiting permanent revocation; does not close borrowed resources or interrupt providers. */
    @Override
    public void close() {
        lifecycle.close();
    }

    /** Worker-only bounded drain after close, including calls waiting to mutate the chain. */
    public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
        return lifecycle.awaitStopped(timeout, unit);
    }

    private void appendCommitment(BitcoinBlockReference block) {
        KarmaChainEntry commitment = chain.commitEpoch(block, signingKey);
        table.append(commitment);
    }
}
