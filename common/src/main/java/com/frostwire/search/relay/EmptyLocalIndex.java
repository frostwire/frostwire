/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A {@link LocalIndex} that holds nothing. Used by FORWARDER-class
 * IceBridge nodes, which participate in the search protocol only to
 * dual-envelope-forward requests to peers that do have indexes —
 * a pure forwarder never answers from local content.
 */
public final class EmptyLocalIndex implements LocalIndex {

    @Override
    public void upsert(LocalSharedTorrent torrent) {
    }

    @Override
    public void delete(String infoHashHex) {
    }

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
        return Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
        return new ArrayList<>();
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {
    }

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
        return new ArrayList<>();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {
    }

    @Override
    public int size() {
        return 0;
    }
}
