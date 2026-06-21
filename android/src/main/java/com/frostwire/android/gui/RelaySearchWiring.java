/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;

/**
 * Holds the relay-stack wiring dependencies (localIndex, karmaCache,
 * peerDirectory, identity, searchTransport) needed by the LOCAL and
 * DISTRIBUTED search engines.
 *
 * <p>Composed into only those two engines — the 14 web-scraping engines
 * carry zero overhead. All fields are volatile for cross-thread visibility
 * (wired on a background thread, read on the search thread).
 */
public final class RelaySearchWiring {

    private volatile LocalIndex localIndex;
    private volatile PeerKarmaCache karmaCache;
    private volatile PeerDirectory peerDirectory;
    private volatile IdentityKeys identity;
    private volatile DistributedSearchTransport searchTransport;

    public LocalIndex localIndex() {
        return localIndex;
    }

    public RelaySearchWiring localIndex(LocalIndex localIndex) {
        this.localIndex = localIndex;
        return this;
    }

    public PeerKarmaCache karmaCache() {
        return karmaCache;
    }

    public RelaySearchWiring karmaCache(PeerKarmaCache karmaCache) {
        this.karmaCache = karmaCache;
        return this;
    }

    public PeerDirectory peerDirectory() {
        return peerDirectory;
    }

    public RelaySearchWiring peerDirectory(PeerDirectory peerDirectory) {
        this.peerDirectory = peerDirectory;
        return this;
    }

    public IdentityKeys identity() {
        return identity;
    }

    public RelaySearchWiring identity(IdentityKeys identity) {
        this.identity = identity;
        return this;
    }

    public DistributedSearchTransport searchTransport() {
        return searchTransport;
    }

    public RelaySearchWiring searchTransport(DistributedSearchTransport searchTransport) {
        this.searchTransport = searchTransport;
        return this;
    }
}
