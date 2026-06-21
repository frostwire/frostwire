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

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.DhtAdvertiser;
import com.frostwire.search.relay.DhtPeerDiscoverySource;
import com.frostwire.search.relay.DirectTcpPeerAuthenticator;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.IndexAnnouncementPublisher;
import com.frostwire.search.relay.KarmaChainSource;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerDiscovery;
import com.frostwire.search.relay.PeerDiscoveryScheduler;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.SharedTorrentIndexerInstaller;
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
 * Android runs {@link IceBridgeServer} directly within the app process.
 *
 * <p>Phase 2: identity, in-process IceBridgeServer, IceBridgeClient (OkHttp),
 * IceBridgeSearchTransport, RelaySearchService, IncomingSearchRequestHandler.
 *
 * <p>Phase 3: SharedTorrentIndexer (indexes downloads into LocalIndex),
 * PeerDirectory (tracks discovered peers with trust scores), DhtPeerDiscoverySource
 * (BEP 5 rendezvous), PeerDiscoveryScheduler (periodic discovery),
 * DhtAdvertiser (BEP 46 identity + BEP 5 index announcement),
 * PeerRegistrySync (syncs verified peers into IceBridge routing table).
 *
 * <p>Phase 4 (Karma): PeerKarmaCache currently uses a no-op source; will be
 * replaced with DhtKarmaChainSource when Karma is wired.
 *
 * <p><b>Threading:</b> {@link #start} must be called off the main thread.
 * BTEngine must already be started before calling {@code start}.
 *
 * <p><b>Shutdown ordering:</b> reverse of startup —
 * dhtAdvertiser → peerDiscoveryScheduler → peerRegistrySync → incomingHandler →
 * transport → client → server → localIndex.
 */
public final class AndroidRelayStack implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AndroidRelayStack.class);

    private static final long PEER_DISCOVERY_INTERVAL_SEC = 5 * 60;
    private static final long DHT_ADVERTISE_INTERVAL_SEC = RelayConstants.IDENTITY_REPUBLISH_INTERVAL_SEC;

    private final AndroidLocalIndex localIndex;
    private final IdentityKeys identity;
    private final IceBridgeServer server;
    private final IceBridgeClient client;
    private final IceBridgeSearchTransport transport;
    private final RelaySearchService searchService;
    private final IncomingSearchRequestHandler incomingHandler;
    private final PeerDirectory peerDirectory;
    private final PeerRegistrySync peerRegistrySync;
    private final PeerDiscoveryScheduler peerDiscoveryScheduler;
    private final DhtAdvertiser dhtAdvertiser;

    /**
     * Start the relay stack. All heavy work is done on the calling thread.
     * If startup fails partway through, all resources started so far are
     * cleaned up before returning {@code null}.
     *
     * @param context Android context (used for DB and file paths)
     * @param homeDir app-private directory for identity.dat and DB files
     * @param btEngine the running BTEngine (must already be started)
     * @return the started stack, or {@code null} on failure
     */
    public static AndroidRelayStack start(Context context, File homeDir, BTEngine btEngine) {
        AndroidLocalIndex li = null;
        IceBridgeServer srv = null;
        IceBridgeClient cl = null;
        IceBridgeSearchTransport tr = null;
        IncomingSearchRequestHandler ih = null;
        PeerDirectory pd = null;
        PeerRegistrySync prs = null;
        PeerDiscoveryScheduler pds = null;
        DhtAdvertiser da = null;
        try {
            li = AndroidLocalIndex.open(context);

            File identityFile = new File(homeDir, RelayConstants.IDENTITY_FILE);
            IdentityKeys ident = IdentityKeys.loadOrCreate(identityFile);
            LOG.info("AndroidRelayStack: identity loaded: "
                    + com.frostwire.util.Hex.encode(ident.ed25519PubRaw()));

            SharedTorrentIndexerInstaller.install(btEngine, li, ident);
            LOG.info("AndroidRelayStack: SharedTorrentIndexer installed");

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

            PeerKarmaCache karmaCache = new PeerKarmaCache(
                    new RemoteKarmaChainFetcher(new NoOpKarmaChainSource()));
            pd = new PeerDirectory(karmaCache);

            RelaySearchService ss = new RelaySearchService(li, ident);

            ih = new IncomingSearchRequestHandler(tr, ss, pd, ident, li);
            ih.start();

            String localHost = "127.0.0.1";
            prs = new PeerRegistrySync(cl, pd, localHost, srv.rudpPort());
            prs.start();
            LOG.info("AndroidRelayStack: PeerRegistrySync started");

            DhtPeerDiscoverySource discoverySource = new DhtPeerDiscoverySource(btEngine);
            PeerDiscovery discovery = new PeerDiscovery(discoverySource, pd,
                    new DirectTcpPeerAuthenticator());
            pds = new PeerDiscoveryScheduler(discovery, PEER_DISCOVERY_INTERVAL_SEC);
            pds.start();
            LOG.info("AndroidRelayStack: PeerDiscoveryScheduler started");

            IdentityRecordPublisher identityPublisher =
                    new IdentityRecordPublisher(ident, srv.rudpPort());
            IndexAnnouncementPublisher indexPublisher =
                    new IndexAnnouncementPublisher(li, ident);
            da = new DhtAdvertiser(identityPublisher, indexPublisher, DHT_ADVERTISE_INTERVAL_SEC);
            da.start();
            LOG.info("AndroidRelayStack: DhtAdvertiser started");

            AndroidRelayStack stack = new AndroidRelayStack(li, ident, srv, cl, tr, ss, ih,
                    pd, prs, pds, da);
            li = null;
            srv = null;
            cl = null;
            tr = null;
            ih = null;
            pd = null;
            prs = null;
            pds = null;
            da = null;
            LOG.info("AndroidRelayStack: started successfully");
            return stack;
        } catch (Throwable t) {
            LOG.warn("Failed to start AndroidRelayStack", t);
            if (da != null) try { da.stop(); } catch (Throwable ignored) {}
            if (pds != null) try { pds.stop(); } catch (Throwable ignored) {}
            if (prs != null) try { prs.close(); } catch (Throwable ignored) {}
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
                              IncomingSearchRequestHandler incomingHandler,
                              PeerDirectory peerDirectory,
                              PeerRegistrySync peerRegistrySync,
                              PeerDiscoveryScheduler peerDiscoveryScheduler,
                              DhtAdvertiser dhtAdvertiser) {
        this.localIndex = localIndex;
        this.identity = identity;
        this.server = server;
        this.client = client;
        this.transport = transport;
        this.searchService = searchService;
        this.incomingHandler = incomingHandler;
        this.peerDirectory = peerDirectory;
        this.peerRegistrySync = peerRegistrySync;
        this.peerDiscoveryScheduler = peerDiscoveryScheduler;
        this.dhtAdvertiser = dhtAdvertiser;
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

    public PeerDirectory peerDirectory() {
        return peerDirectory;
    }

    @Override
    public void close() {
        LOG.info("AndroidRelayStack: shutting down...");
        try { if (dhtAdvertiser != null) dhtAdvertiser.stop(); } catch (Throwable t) { LOG.warn("Error stopping DhtAdvertiser", t); }
        try { if (peerDiscoveryScheduler != null) peerDiscoveryScheduler.stop(); } catch (Throwable t) { LOG.warn("Error stopping PeerDiscoveryScheduler", t); }
        try { if (peerRegistrySync != null) peerRegistrySync.close(); } catch (Throwable t) { LOG.warn("Error closing PeerRegistrySync", t); }
        try { if (incomingHandler != null) incomingHandler.stop(); } catch (Throwable t) { LOG.warn("Error stopping IncomingSearchRequestHandler", t); }
        try { if (transport != null) transport.close(); } catch (Throwable t) { LOG.warn("Error closing transport", t); }
        try { if (client != null) client.close(); } catch (Throwable t) { LOG.warn("Error closing client", t); }
        try { if (server != null) server.close(); } catch (Throwable t) { LOG.warn("Error closing server", t); }
        try { if (localIndex != null) localIndex.close(); } catch (Throwable t) { LOG.warn("Error closing localIndex", t); }
        LOG.info("AndroidRelayStack: shutdown complete");
    }

    /**
     * No-op KarmaChainSource for Phase 3. Returns null for all fetches,
     * meaning all peers have a karma score of 0. Replaced with
     * DhtKarmaChainSource in Phase 4.
     */
    private static final class NoOpKarmaChainSource implements KarmaChainSource {
        @Override
        public Entry fetchManifest(byte[] peerPub) {
            return null;
        }
    }
}
