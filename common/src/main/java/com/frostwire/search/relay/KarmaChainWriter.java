/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.security.PrivateKey;
import java.util.concurrent.locks.ReentrantLock;

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
 * <p>Thread-safety: all mutating operations are serialized through a
 * {@link ReentrantLock}. The in-memory {@link KarmaChain} already
 * synchronizes internally; the additional lock prevents a download
 * completion and a periodic epoch commit from racing.
 *
 * <p>Fail-closed: any error resolving a Bitcoin block, signing, or
 * persisting is logged and swallowed. The download itself is never
 * affected by karma bookkeeping failures.
 *
 * <p><b>Scope of this build:</b> the in-memory chain is reconstructed
 * from genesis on each app startup. Loading persisted entries on
 * startup is a future feature; see {@link KarmaChainStore#loadChain}.
 */
public final class KarmaChainWriter implements KarmaEndorsementSink {

    private static final Logger LOG = Logger.getLogger(KarmaChainWriter.class);

    private final byte[] ownerPub;
    private final PrivateKey signingKey;
    private final BlockHeaderSource blockSource;
    private final KarmaChainStore table;
    private final KarmaChain chain;
    private final ReentrantLock writeLock = new ReentrantLock();

    public KarmaChainWriter(IdentityKeys identity,
                            BlockHeaderSource blockSource,
                            KarmaChainStore table) {
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
        writeLock.lock();
        try {
            long tip = blockSource.getChainTipHeight();
            if (tip < 0) {
                return;
            }
            BitcoinBlockReference block = blockSource.getBlock(tip);
            if (block == null) {
                return;
            }
            if (block.epoch() <= chain.currentEpoch()) {
                return;
            }
            appendCommitment(block);
        } catch (Throwable t) {
            LOG.warn("KarmaChainWriter.commitEpochIfNeeded failed", t);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void onDownloadCompletedFromPeer(byte[] peerEd25519Pub, byte[] infoHash) {
        if (peerEd25519Pub == null) {
            return;
        }
        if (infoHash == null) {
            return;
        }
        writeLock.lock();
        try {
            long tip = blockSource.getChainTipHeight();
            if (tip < 0) {
                LOG.debug("Skipping endorsement: no Bitcoin chain tip available");
                return;
            }
            BitcoinBlockReference block = blockSource.getBlock(tip);
            if (block == null) {
                LOG.debug("Skipping endorsement: could not resolve tip block " + tip);
                return;
            }
            if (block.epoch() > chain.currentEpoch()) {
                appendCommitment(block);
            }
            if (chain.availableEnergy() <= 0) {
                LOG.debug("Skipping endorsement: energy budget exhausted for epoch " +
                        chain.currentEpoch());
                return;
            }
            try {
                KarmaChainEntry endorsement = chain.endorse(
                        peerEd25519Pub, infoHash, block, signingKey);
                table.append(endorsement);
            } catch (IllegalStateException e) {
                LOG.debug("Endorsement rejected by chain: " + e.getMessage());
            }
        } catch (Throwable t) {
            LOG.warn("KarmaChainWriter.onDownloadCompletedFromPeer failed", t);
        } finally {
            writeLock.unlock();
        }
    }

    private void appendCommitment(BitcoinBlockReference block) {
        KarmaChainEntry commitment = chain.commitEpoch(block, signingKey);
        table.append(commitment);
    }
}
