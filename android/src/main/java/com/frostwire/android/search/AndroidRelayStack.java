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

package com.frostwire.android.search;

import android.content.Context;

import com.frostwire.android.search.AndroidLocalIndex;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
import com.frostwire.util.Logger;

import java.io.File;

/**
 * Wires up the IceBridge distributed search stack in-process on Android.
 *
 * <p>Unlike desktop (which spawns a separate {@code icebridge.jar} subprocess),
 * Android runs {@link IceBridgeServer} directly within the app process. This
 * avoids the complexity of process spawning on Android and lets the server
 * share the app's lifecycle.
 *
 * <p>Phase 2 scope: identity load/create, in-process IceBridgeServer,
 * IceBridgeClient (OkHttp) → IceBridgeSearchTransport, RelaySearchService,
 * IncomingSearchRequestHandler as permanent listener. Uses
 * {@code PeerRegistrySync.ICEBRIDGE_RUDP_PORT} as the well-known rUDP port;
 * full PeerRegistrySync wiring requires PeerDirectory (Phase 3).
 *
 * <p>PeerDirectory, SharedTorrentIndexer, Karma, and DHT advertising are
 * Phases 3-4.
 */
public final class AndroidRelayStack implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AndroidRelayStack.class);

    private final AndroidLocalIndex localIndex;
    private final IdentityKeys identity;
    private final IceBridgeServer server;
    private final IceBridgeClient client;
    private final IceBridgeSearchTransport transport;
    private final RelaySearchService searchService;
    private final IncomingSearchRequestHandler incomingHandler;

    /**
     * Start the relay stack. All heavy work is done on the calling thread —
     * callers MUST ensure this is called off the main thread.
     *
     * <p>If startup fails partway through, all resources started so far are
     * cleaned up before returning {@code null}. This ensures a retry can
     * re-bind the rUDP port without "address already in use" errors.
     *
     * @param context Android context (used for DB and file paths)
     * @param homeDir app-private directory for identity.dat and DB files
     * @return the started stack, or {@code null} on failure
     */
    public static AndroidRelayStack start(Context context, File homeDir) {
        AndroidLocalIndex li = null;
        IceBridgeServer srv = null;
        IceBridgeClient cl = null;
        IceBridgeSearchTransport tr = null;
        IncomingSearchRequestHandler ih = null;
        try {
            li = AndroidLocalIndex.open(context);

            File identityFile = new File(homeDir, RelayConstants.IDENTITY_FILE);
            IdentityKeys ident = IdentityKeys.loadOrCreate(identityFile);
            LOG.info("AndroidRelayStack: identity loaded: "
                    + com.frostwire.util.Hex.encode(ident.ed25519PubRaw()));

            IceBridgeConfig config = IceBridgeConfig.newBuilder()
                    .host("0.0.0.0")
                    .rudpPort(PeerRegistrySync.ICEBRIDGE_RUDP_PORT)
                    .controlHttpPort(0)
                    .role(IceBridgeConfig.Role.BOTH)
                    .identityFile(identityFile)
                    .maxPeers(500)
                    .peerTtlSec(180)
                    .maxQpsPerKey(5.0)
                    .build();

            srv = new IceBridgeServer(config);
            srv.start();
            LOG.info("AndroidRelayStack: IceBridgeServer started: rudpPort=" + srv.rudpPort()
                    + " controlPort=" + srv.controlPort());

            cl = new IceBridgeClient(srv.controlPort());
            cl.setAuthToken(srv.authToken());

            tr = new IceBridgeSearchTransport(cl);
            tr.start();

            RelaySearchService ss = new RelaySearchService(li, ident);

            ih = new IncomingSearchRequestHandler(tr, ss, null, ident, li);
            ih.start();

            AndroidRelayStack stack = new AndroidRelayStack(li, ident, srv, cl, tr, ss, ih);
            li = null;
            srv = null;
            cl = null;
            tr = null;
            ih = null;
            LOG.info("AndroidRelayStack: started successfully");
            return stack;
        } catch (Throwable t) {
            LOG.warn("Failed to start AndroidRelayStack", t);
            if (ih != null) try { ih.stop(); } catch (Throwable ignored) {}
            if (tr != null) try { tr.close(); } catch (Throwable ignored) {}
            if (cl != null) try { cl.close(); } catch (Throwable ignored) {}
            if (srv != null) try { srv.close(); } catch (Throwable ignored) {}
            if (li != null) try { li.close(); } catch (Throwable ignored) {}
            return null;
        }
    }

    private AndroidRelayStack(AndroidLocalIndex localIndex,
                              IdentityKeys identity,
                              IceBridgeServer server,
                              IceBridgeClient client,
                              IceBridgeSearchTransport transport,
                              RelaySearchService searchService,
                              IncomingSearchRequestHandler incomingHandler) {
        this.localIndex = localIndex;
        this.identity = identity;
        this.server = server;
        this.client = client;
        this.transport = transport;
        this.searchService = searchService;
        this.incomingHandler = incomingHandler;
    }

    public AndroidLocalIndex localIndex() {
        return localIndex;
    }

    public IdentityKeys identity() {
        return identity;
    }

    public IceBridgeServer server() {
        return server;
    }

    public IceBridgeSearchTransport transport() {
        return transport;
    }

    public RelaySearchService searchService() {
        return searchService;
    }

    @Override
    public void close() {
        LOG.info("AndroidRelayStack: shutting down...");
        try {
            if (incomingHandler != null) {
                incomingHandler.stop();
            }
        } catch (Throwable t) {
            LOG.warn("Error stopping IncomingSearchRequestHandler", t);
        }
        try {
            if (transport != null) {
                transport.close();
            }
        } catch (Throwable t) {
            LOG.warn("Error closing transport", t);
        }
        try {
            if (client != null) {
                client.close();
            }
        } catch (Throwable t) {
            LOG.warn("Error closing client", t);
        }
        try {
            if (server != null) {
                server.close();
            }
        } catch (Throwable t) {
            LOG.warn("Error closing server", t);
        }
        try {
            if (localIndex != null) {
                localIndex.close();
            }
        } catch (Throwable t) {
            LOG.warn("Error closing localIndex", t);
        }
        LOG.info("AndroidRelayStack: shutdown complete");
    }
}
