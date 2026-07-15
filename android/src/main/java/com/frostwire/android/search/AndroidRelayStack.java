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

import com.frostwire.android.gui.SearchEngine;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.search.relay.BTEngineListenerChain;
import com.frostwire.search.relay.DhtAdvertiser;
import com.frostwire.search.relay.DhtKarmaChainSource;
import com.frostwire.search.relay.DhtPeerDiscoverySource;
import com.frostwire.search.relay.DirectTcpPeerAuthenticator;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.IndexAnnouncementPublisher;
import com.frostwire.search.relay.IncomingRelayServer;
import com.frostwire.search.relay.KarmaChainCommitScheduler;
import com.frostwire.search.relay.KarmaChainPublisher;
import com.frostwire.search.relay.KarmaChainStore;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.KarmaEndorsementTrigger;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerDiscovery;
import com.frostwire.search.relay.PeerDiscoveryScheduler;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.search.relay.RelayRole;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.search.relay.SharedTorrentIndexer;
import com.frostwire.search.relay.SharedTorrentIndexerInstaller;
import com.frostwire.transfers.Transfer;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
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
 * <p>Phase 4 (Karma): KarmaChainStore (Android SQLite), KarmaChainWriter
 * (Bitcoin-anchored endorsements), KarmaEndorsementTrigger (fires on
 * download completion), KarmaChainCommitScheduler (periodic commit +
 * DHT publish), PeerKarmaCache via DhtKarmaChainSource.
 *
 * <p><b>Threading:</b> {@link #start} must be called off the main thread.
 * BTEngine must already be started before calling {@code start}.
 *
 * <p><b>Shutdown ordering:</b> reverse of startup —
 * karmaScheduler → dhtAdvertiser → peerDiscoveryScheduler → peerRegistrySync → incomingHandler →
 * transport → client → server → karmaStore → localIndex.
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
    private final KarmaChainStore karmaStore;
    private final KarmaChainCommitScheduler karmaScheduler;
    private final IncomingRelayServer relayServer;

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
        AndroidKarmaChainStore ks = null;
        KarmaChainCommitScheduler kcs = null;
        IncomingRelayServer relaySrv = null;
        try {
            // Identity first — LocalIndex.open can take seconds and must not delay
            // Settings showing Node ID after a cold start / force-stop.
            File identityFile = new File(homeDir, RelayConstants.IDENTITY_FILE);
            IdentityKeys ident = IdentityKeys.loadOrCreate(identityFile);
            LOG.info("AndroidRelayStack: identity loaded: "
                    + com.frostwire.util.Hex.encode(ident.ed25519PubRaw()));
            SearchEngine.DISTRIBUTED_WIRING.identity(ident);

            li = AndroidLocalIndex.open(context);

            SharedTorrentIndexer indexer =
                    SharedTorrentIndexerInstaller.install(btEngine, li, ident);
            // Torrents restored before this listener was chained never fired
            // downloadAdded — reindex them so Local search is not empty.
            try {
                java.util.ArrayList<BTDownload> existing = new java.util.ArrayList<>();
                for (Transfer t : TransferManager.instance().getTransfers()) {
                    if (t instanceof UIBittorrentDownload) {
                        BTDownload dl = ((UIBittorrentDownload) t).getDl();
                        if (dl != null) {
                            existing.add(dl);
                        }
                    }
                }
                indexer.indexExisting(existing);
                LOG.info("AndroidRelayStack: SharedTorrentIndexer installed; reindex scheduled for "
                        + existing.size() + " transfer(s)");
            } catch (Throwable t) {
                LOG.warn("AndroidRelayStack: SharedTorrentIndexer installed; reindex failed", t);
            }

            ks = new AndroidKarmaChainStore(context, AndroidLocalIndex.DEFAULT_DB_NAME);
            File bitcoinCacheDir = new File(homeDir, RelayConstants.BITCOIN_HEADER_CACHE_DIR);
            com.frostwire.search.relay.BlockHeaderSource blockSource =
                    new com.frostwire.search.relay.HttpBlockHeaderFetcher(bitcoinCacheDir);
            KarmaChainWriter karmaWriter = new KarmaChainWriter(ident, blockSource, ks);
            BTEngineListenerChain.install(btEngine,
                    new KarmaEndorsementTrigger(li, ident.ed25519PubRaw(), karmaWriter));
            KarmaChainPublisher karmaPublisher = new KarmaChainPublisher(karmaWriter, ident);
            kcs = new KarmaChainCommitScheduler(karmaWriter, karmaPublisher,
                    RelayConstants.KARMA_COMMIT_INTERVAL_SEC);
            kcs.start();
            LOG.info("AndroidRelayStack: Karma chain wired");

            // Persist IceBridge host cache under app-private libtorrent dir (not ~/.frostwire).
            File hostCacheFile = new File(homeDir, "icebridge_host_cache.txt");
            IceBridgeHostCache.configure(hostCacheFile);

            int controlPort = freeLocalControlPort();
            IceBridgeConfig config = IceBridgeConfig.newBuilder()
                    .host("0.0.0.0")
                    .rudpPort(PeerRegistrySync.ICEBRIDGE_RUDP_PORT)
                    .controlHttpPort(controlPort)
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
                    new RemoteKarmaChainFetcher(new DhtKarmaChainSource(btEngine)));
            pd = new PeerDirectory(karmaCache);

            IncomingRelayServer relaySrv2 = null;
            try {
                int relayPort = RelayConstants.RELAY_LISTEN_PORT;
                RelaySearchService relayService = new RelaySearchService(li, ident);
                RelayRole relayRole = new RelayRole(relayService, pd, ident);
                IdentityRecord identityRecord = IdentityRecord.createSigned(
                        ident.nodeId(), ident.ed25519(),
                        ident.x25519PubRaw(), relayPort, srv.rudpPort(), "BOTH");
                relaySrv2 = new IncomingRelayServer(relayRole, identityRecord, relayPort);
                relaySrv2.start();
                relaySrv = relaySrv2;
                LOG.info("AndroidRelayStack: IncomingRelayServer started on port " + relayPort);
                // Self + local control surface for settings "known IceBridge servers".
                try {
                    IceBridgeHostCache.getInstance()
                            .markSuccess("127.0.0.1", relayPort, "BOTH");
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                LOG.warn("AndroidRelayStack: Failed to start IncomingRelayServer", t);
            }

            RelaySearchService ss = new RelaySearchService(li, ident);

            ih = new IncomingSearchRequestHandler(tr, ss, pd, ident, li);
            ih.start();

            // Never call InetAddress.getLocalHost() on Android — it can block for
            // tens of seconds (or longer) on DNS/reverse-lookup failure, freezing
            // the MISC handler that starts the relay stack and identity UI.
            String localHost = "127.0.0.1";
            try {
                String adv = System.getProperty("frostwire.icebridge.advertiseHost");
                if (adv != null && !adv.isEmpty()) {
                    localHost = adv;
                }
            } catch (Throwable ignored) {
            }
            prs = new PeerRegistrySync(cl, pd, localHost, srv.rudpPort(), ident,
                    IceBridgeConfig.Role.BOTH);
            prs.start();
            LOG.info("AndroidRelayStack: PeerRegistrySync started advertiseHost=" + localHost);

            DhtPeerDiscoverySource discoverySource = new DhtPeerDiscoverySource(btEngine);
            byte[] ownPub = (ident != null) ? ident.ed25519PubRaw() : null;
            PeerDiscovery discovery = new PeerDiscovery(discoverySource, pd,
                    new DirectTcpPeerAuthenticator(), ownPub);
            pds = new PeerDiscoveryScheduler(discovery, PEER_DISCOVERY_INTERVAL_SEC);
            pds.start();
            LOG.info("AndroidRelayStack: PeerDiscoveryScheduler started");

            IdentityRecordPublisher identityPublisher =
                    new IdentityRecordPublisher(ident, RelayConstants.RELAY_LISTEN_PORT, srv.rudpPort(), "BOTH");
            IndexAnnouncementPublisher indexPublisher =
                    new IndexAnnouncementPublisher(li, ident);
            da = new DhtAdvertiser(identityPublisher, indexPublisher, DHT_ADVERTISE_INTERVAL_SEC);
            da.start();
            LOG.info("AndroidRelayStack: DhtAdvertiser started");

            if (li == null || karmaCache == null || pd == null || ident == null || tr == null) {
                throw new IllegalStateException("Wiring inputs must be non-null");
            }
            SearchEngine.LOCAL_WIRING
                    .localIndex(li)
                    .karmaCache(karmaCache);
            SearchEngine.DISTRIBUTED_WIRING
                    .localIndex(li)
                    .peerDirectory(pd)
                    .identity(ident)
                    .searchTransport(tr);
            LOG.info("AndroidRelayStack: LOCAL and DISTRIBUTED search engines wired");

            AndroidRelayStack stack = new AndroidRelayStack(li, ident, srv, cl, tr, ss, ih,
                    pd, prs, pds, da, ks, kcs, relaySrv);
            li = null;
            srv = null;
            cl = null;
            tr = null;
            ih = null;
            pd = null;
            prs = null;
            pds = null;
            da = null;
            ks = null;
            kcs = null;
            relaySrv = null;
            LOG.info("AndroidRelayStack: started successfully");
            return stack;
        } catch (Throwable t) {
            LOG.warn("Failed to start AndroidRelayStack", t);
            if (kcs != null) try { kcs.stop(); } catch (Throwable ignored) {}
            if (da != null) try { da.stop(); } catch (Throwable ignored) {}
            if (pds != null) try { pds.stop(); } catch (Throwable ignored) {}
            if (relaySrv != null) try { relaySrv.stop(); } catch (Throwable ignored) {}
            if (prs != null) try { prs.close(); } catch (Throwable ignored) {}
            if (ih != null) try { ih.stop(); } catch (Throwable ignored) {}
            if (tr != null) try { tr.close(); } catch (Throwable ignored) {}
            if (cl != null) try { cl.close(); } catch (Throwable ignored) {}
            if (srv != null) try { srv.close(); } catch (Throwable ignored) {}
            if (ks != null) try { ks.close(); } catch (Throwable ignored) {}
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
                              DhtAdvertiser dhtAdvertiser,
                              KarmaChainStore karmaStore,
                              KarmaChainCommitScheduler karmaScheduler,
                              IncomingRelayServer relayServer) {
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
        this.karmaStore = karmaStore;
        this.karmaScheduler = karmaScheduler;
        this.relayServer = relayServer;
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

    private static int freeLocalControlPort() throws java.io.IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close() {
        LOG.info("AndroidRelayStack: shutting down...");
        try { if (karmaScheduler != null) karmaScheduler.stop(); } catch (Throwable t) { LOG.warn("Error stopping KarmaChainCommitScheduler", t); }
        try { if (dhtAdvertiser != null) dhtAdvertiser.stop(); } catch (Throwable t) { LOG.warn("Error stopping DhtAdvertiser", t); }
        try { if (relayServer != null) relayServer.stop(); } catch (Throwable t) { LOG.warn("Error stopping IncomingRelayServer", t); }
        try { if (peerDiscoveryScheduler != null) peerDiscoveryScheduler.stop(); } catch (Throwable t) { LOG.warn("Error stopping PeerDiscoveryScheduler", t); }
        try { if (peerRegistrySync != null) peerRegistrySync.close(); } catch (Throwable t) { LOG.warn("Error closing PeerRegistrySync", t); }
        try { if (incomingHandler != null) incomingHandler.stop(); } catch (Throwable t) { LOG.warn("Error stopping IncomingSearchRequestHandler", t); }
        try { if (transport != null) transport.close(); } catch (Throwable t) { LOG.warn("Error closing transport", t); }
        try { if (client != null) client.close(); } catch (Throwable t) { LOG.warn("Error closing client", t); }
        try { if (server != null) server.close(); } catch (Throwable t) { LOG.warn("Error closing server", t); }
        try { if (karmaStore != null) karmaStore.close(); } catch (Throwable t) { LOG.warn("Error closing karmaStore", t); }
        try { if (localIndex != null) localIndex.close(); } catch (Throwable t) { LOG.warn("Error closing localIndex", t); }
        LOG.info("AndroidRelayStack: shutdown complete");
    }
}
