/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Decides whether a local-index row may appear in Local search results and
 * in answers to remote distributed-search queries.
 *
 * <p>Default product policy: only torrents the user is still actively
 * transferring (in session with metadata / in the swarm). Historical
 * index rows are hidden unless the user enables "include inactive".
 */
@FunctionalInterface
public interface ShareVisibilityPolicy {

    /** Always include (used when "include inactive shares" is on). */
    ShareVisibilityPolicy INCLUDE_ALL = infoHashHex -> true;

    /**
     * @param infoHashHex 40-char hex info-hash (any case)
     * @return true if this torrent may be returned from local/distributed search
     */
    boolean isVisible(String infoHashHex);
}
