/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for the local distributed-search torrent index.
 *
 * <p>Desktop uses a SQLite + FTS5 implementation. Headless relayd may use
 * an in-memory implementation or no-op implementation depending on role.
 */
public interface LocalIndex {
    void upsert(LocalSharedTorrent torrent);

    void delete(String infoHashHex);

    Optional<LocalSharedTorrent> get(String infoHashHex);

    List<LocalSharedTorrent> search(String query, int limit);

    void markPublished(String infoHashHex, long timestamp);

    List<String> needsRepublish(long nowSec, long thresholdSec);

    void updateLastSeen(String infoHashHex, long ts);

    int size();
}
