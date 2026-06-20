/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerDirectory;

/**
 * Tiny installer that wires the user-facing DISTRIBUTED search engine.
 *
 * <p>All four dependencies must be installed before the engine reports
 * itself as ready: a {@link LocalIndex} for the local half of the search,
 * a {@link PeerDirectory} for authenticated peers, the node's
 * {@link IdentityKeys} for signing requests, and a
 * {@link DistributedSearchTransport} for sending and receiving payloads
 * over IceBridge.
 */
public final class DistributedSearchEngineWire {
    private static volatile PeerDirectory peerDirectoryRef;

    private DistributedSearchEngineWire() {
    }

    public static void wire(LocalIndex localIndex,
                            PeerDirectory peerDirectory,
                            IdentityKeys identity,
                            DistributedSearchTransport transport) {
        if (localIndex == null) {
            throw new IllegalArgumentException("localIndex is null");
        }
        if (peerDirectory == null) {
            throw new IllegalArgumentException("peerDirectory is null");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        if (transport == null) {
            throw new IllegalArgumentException("transport is null");
        }
        SearchEngine distributed = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
        if (distributed == null) {
            throw new IllegalStateException("DISTRIBUTED search engine is not registered");
        }
        distributed.setLocalIndex(localIndex)
                .setPeerDirectory(peerDirectory)
                .setIdentityKeys(identity)
                .setSearchTransport(transport);
        peerDirectoryRef = peerDirectory;
    }

    public static PeerDirectory getPeerDirectory() {
        return peerDirectoryRef;
    }
}
