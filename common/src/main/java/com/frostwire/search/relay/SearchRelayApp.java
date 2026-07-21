/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
import com.frostwire.util.Logger;

/**
 * Search application layer (Protocol #1) for a standalone IceBridge
 * FORWARDER/BOTH node. Composes over a started {@link IceBridgeServer}
 * without touching the fabric: answers from an {@link EmptyLocalIndex}
 * (a pure forwarder has no content) and dual-envelope-forwards signed
 * search requests to mesh peers imported from the local registry.
 *
 * <p>This is what lets a client that knows only one hub (e.g. from its
 * host cache) reach index-holding peers behind it: the client addresses
 * the hub, the hub forwards to the real holders, and their signed
 * responses route back over the mesh. The control plane stays local —
 * the app talks to the server's own loopback control API only.
 */
public final class SearchRelayApp implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SearchRelayApp.class);

    private final IceBridgeSearchTransport transport;
    private final IncomingSearchRequestHandler handler;
    private final PeerRegistrySync registrySync;
    private final PeerDirectory directory;

    private SearchRelayApp(IceBridgeSearchTransport transport,
                           IncomingSearchRequestHandler handler,
                           PeerRegistrySync registrySync,
                           PeerDirectory directory) {
        this.transport = transport;
        this.handler = handler;
        this.registrySync = registrySync;
        this.directory = directory;
    }

    public static SearchRelayApp start(IceBridgeServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server is null");
        }
        IceBridgeClient client = new IceBridgeClient(server.controlPort());
        client.setAuthToken(server.authToken());
        LocalIndex emptyIndex = new EmptyLocalIndex();
        PeerDirectory directory = new PeerDirectory(new PeerKarmaCache(
                new RemoteKarmaChainFetcher(peerPub -> null)));

        IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
        transport.start();

        RelaySearchService service = new RelaySearchService(emptyIndex, server.identity());
        IncomingSearchRequestHandler handler = new IncomingSearchRequestHandler(
                transport, service, directory, server.identity(), emptyIndex);
        handler.start();

        // Registry → directory import only (identity null skips self-register;
        // the server owns its own self-entry and we must not overwrite it with
        // a wrong advertise host).
        PeerRegistrySync registrySync = new PeerRegistrySync(
                client, directory, "127.0.0.1", server.rudpPort(), null, null);
        registrySync.start();

        LOG.info("SearchRelayApp started: dual-envelope forward with empty index");
        return new SearchRelayApp(transport, handler, registrySync, directory);
    }

    /** Visible for tests: the directory fed by registry mesh import. */
    PeerDirectory directory() {
        return directory;
    }

    @Override
    public void close() {
        try {
            if (registrySync != null) {
                registrySync.close();
            }
        } catch (Throwable t) {
            LOG.debug("SearchRelayApp: sync close failed", t);
        }
        try {
            if (handler != null) {
                handler.stop();
            }
        } catch (Throwable t) {
            LOG.debug("SearchRelayApp: handler close failed", t);
        }
        try {
            if (transport != null) {
                transport.close();
            }
        } catch (Throwable t) {
            LOG.debug("SearchRelayApp: transport close failed", t);
        }
    }
}
