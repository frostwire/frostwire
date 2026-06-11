/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BTEngineListener that watches for download completion and fires
 * a {@link KarmaEndorsementSink} callback when the torrent was
 * published by a peer (not by ourselves).
 *
 * <p>Deduplication: tracks already-credited info hashes so each
 * download triggers exactly one endorsement, even if multiple
 * {@code downloadUpdate} events fire after completion.
 *
 * <p>Self-exclusion: endorsements are only fired for peers whose
 * Ed25519 pubkey is non-zero AND differs from our own pubkey.
 */
public final class KarmaEndorsementTrigger implements BTEngineListener {

    private static final Logger LOG = Logger.getLogger(KarmaEndorsementTrigger.class);

    private final LocalIndex index;
    private final byte[] ownEd25519Pub;
    private final KarmaEndorsementSink sink;
    private final Set<String> creditedInfoHashes = ConcurrentHashMap.newKeySet();

    public KarmaEndorsementTrigger(LocalIndex index, byte[] ownEd25519Pub,
                                   KarmaEndorsementSink sink) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        if (ownEd25519Pub == null) {
            throw new IllegalArgumentException("ownEd25519Pub is null");
        }
        if (sink == null) {
            throw new IllegalArgumentException("sink is null");
        }
        this.index = index;
        this.ownEd25519Pub = ownEd25519Pub.clone();
        this.sink = sink;
    }

    @Override
    public void started(BTEngine engine) {
    }

    @Override
    public void stopped(BTEngine engine) {
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
        // No-op: we only act on downloadUpdate when state transitions to FINISHED
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
        if (dl == null) {
            return;
        }
        try {
            TransferState state = dl.getState();
            if (state != TransferState.FINISHED && state != TransferState.SEEDING
                    && !dl.isFinished()) {
                return;
            }
            String infoHashHex;
            try {
                infoHashHex = dl.getInfoHash();
            } catch (Throwable t) {
                LOG.debug("Failed to read info hash from BTDownload", t);
                return;
            }
            if (infoHashHex == null || infoHashHex.isEmpty()) {
                return;
            }
            infoHashHex = infoHashHex.toLowerCase();
            // Deduplicate
            if (!creditedInfoHashes.add(infoHashHex)) {
                return;
            }
            LocalSharedTorrent torrent = index.get(infoHashHex).orElse(null);
            if (torrent == null) {
                return;
            }
            byte[] peerPub = torrent.publisherEd25519Pub();
            if (peerPub == null) {
                return;
            }
            // Exclude self-endorsement
            if (java.util.Arrays.equals(peerPub, ownEd25519Pub)) {
                return;
            }
            // Exclude placeholder zero pubkey
            boolean allZero = true;
            for (byte b : peerPub) {
                if (b != 0) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                return;
            }
            sink.onDownloadCompletedFromPeer(peerPub, hexToBytes(infoHashHex));
        } catch (Throwable t) {
            LOG.warn("KarmaEndorsementTrigger error on downloadUpdate", t);
        }
    }

    private static byte[] hexToBytes(String hex) {
        return com.frostwire.util.Hex.decode(hex);
    }
}
