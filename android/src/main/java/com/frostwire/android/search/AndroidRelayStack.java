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

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
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
import com.frostwire.android.util.SystemUtils;
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
    /** Second reindex after TransferManager.restoreDownloads may still be settling. */
    private static final long DELAYED_REINDEX_MS = 5_000L;

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
            // downloadAdded — reindex now + once more after TransferManager settles.
            reindexExistingTransfers(indexer, "immediate");
            scheduleDelayedReindex(indexer);

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

            // Optional remote IceBridge (same mesh as desktop USE_REMOTE / EC2 forwarder).
            // Priority: system props → libtorrent/icebridge-remote.txt → prefs.
            String remoteUrl = System.getProperty("frostwire.icebridge.remoteUrl", "");
            String remoteToken = System.getProperty("frostwire.icebridge.remoteToken", "");
            boolean useRemote = false;
            File remoteCfg = new File(homeDir, "icebridge-remote.txt");
            if ((remoteUrl == null || remoteUrl.isEmpty()) && remoteCfg.isFile()) {
                try {
                    String[] lines = readRemoteConfigLines(remoteCfg);
                    if (lines.length >= 1 && lines[0] != null && !lines[0].isEmpty()) {
                        remoteUrl = lines[0].trim();
                    }
                    if (lines.length >= 2 && lines[1] != null) {
                        remoteToken = lines[1].trim();
                    }
                    LOG.info("AndroidRelayStack: loaded remote IceBridge config from "
                            + remoteCfg.getAbsolutePath());
                } catch (Throwable t) {
                    LOG.warn("AndroidRelayStack: failed reading " + remoteCfg, t);
                }
            }
            try {
                ConfigurationManager cm = ConfigurationManager.instance();
                if (remoteUrl == null || remoteUrl.isEmpty()) {
                    useRemote = cm.getBoolean(Constants.PREF_KEY_ICEBRIDGE_USE_REMOTE);
                    remoteUrl = cm.getString(Constants.PREF_KEY_ICEBRIDGE_REMOTE_URL, "");
                    remoteToken = cm.getString(Constants.PREF_KEY_ICEBRIDGE_REMOTE_TOKEN, "");
                } else {
                    useRemote = true;
                }
            } catch (Throwable t) {
                LOG.warn("AndroidRelayStack: config read for remote IceBridge failed", t);
            }
            if (remoteUrl != null && !remoteUrl.isEmpty()) {
                useRemote = true;
            }

            int meshRudpPort = PeerRegistrySync.ICEBRIDGE_RUDP_PORT;
            PeerKarmaCache karmaCache = new PeerKarmaCache(
                    new RemoteKarmaChainFetcher(new DhtKarmaChainSource(btEngine)));
            pd = new PeerDirectory(karmaCache);

            if (useRemote) {
                LOG.info("AndroidRelayStack: using remote IceBridge at " + remoteUrl);
                cl = new IceBridgeClient(remoteUrl);
                if (remoteToken != null && !remoteToken.isEmpty()) {
                    cl.setAuthToken(remoteToken);
                }
                // Demux /poll by own pub before first register completes.
                cl.setOwnPub(ident.ed25519PubRaw());
                if (!cl.health()) {
                    LOG.warn("AndroidRelayStack: remote IceBridge health check failed: " + remoteUrl);
                }
                // No in-process IceBridgeServer / identity TCP when remote — forwarder owns mesh.
                srv = null;
                relaySrv = null;
            } else {
                int controlPort = freeLocalControlPort();
                int configuredRudp = readConfiguredRudpPort();
                IceBridgeConfig.Role role = readConfiguredRole();
                // relayPort=0 on IceBridgeServer: identity TCP is owned by IncomingRelayServer below.
                IceBridgeConfig config = IceBridgeConfig.newBuilder()
                        .host("0.0.0.0")
                        .rudpPort(configuredRudp)
                        .relayPort(0)
                        .controlHttpPort(controlPort)
                        .role(role)
                        .identityFile(identityFile)
                        .maxPeers(500)
                        .peerTtlSec(180)
                        .maxQpsPerKey(5.0)
                        .build();

                srv = new IceBridgeServer(config);
                srv.start();
                meshRudpPort = srv.rudpPort();
                LOG.info("AndroidRelayStack: IceBridgeServer started: rudpPort=" + srv.rudpPort()
                        + " controlPort=" + srv.controlPort() + " role=" + role);

                cl = new IceBridgeClient(srv.controlPort());
                cl.setAuthToken(srv.authToken());

                try {
                    int relayPort = readConfiguredRelayPort();
                    String roleLabel = role.name();
                    RelaySearchService relayService = new RelaySearchService(li, ident);
                    relayService.setSeederEndpointProvider(
                            new com.frostwire.search.relay.LibtorrentSeederEndpointProvider());
                    RelayRole relayRole = new RelayRole(relayService, pd, ident);
                    IdentityRecord identityRecord = IdentityRecord.createSigned(
                            ident.nodeId(), ident.ed25519(),
                            ident.x25519PubRaw(), relayPort, meshRudpPort, roleLabel);
                    IncomingRelayServer relaySrv2 =
                            new IncomingRelayServer(relayRole, identityRecord, relayPort);
                    relaySrv2.start();
                    relaySrv = relaySrv2;
                    LOG.info("AndroidRelayStack: IncomingRelayServer started on port " + relayPort);
                    IceBridgeHostCache.getInstance().markSuccess("127.0.0.1", relayPort, roleLabel);
                } catch (Throwable t) {
                    LOG.warn("AndroidRelayStack: Failed to start IncomingRelayServer", t);
                }
            }

            tr = new IceBridgeSearchTransport(cl);
            tr.start();

            RelaySearchService ss = new RelaySearchService(li, ident);
            ss.setSeederEndpointProvider(
                    new com.frostwire.search.relay.LibtorrentSeederEndpointProvider());

            ih = new IncomingSearchRequestHandler(tr, ss, pd, ident, li);
            ih.start();

            // USE_REMOTE clients register as local rUDP endpoint of the forwarder so
            // inbound mesh traffic is delivered to /poll (see RudpSessionManager).
            String localHost = "127.0.0.1";
            try {
                String adv = System.getProperty("frostwire.icebridge.advertiseHost");
                if (adv != null && !adv.isEmpty()) {
                    localHost = adv;
                }
            } catch (Throwable ignored) {
            }
            IceBridgeConfig.Role syncRole = readConfiguredRole();
            prs = new PeerRegistrySync(cl, pd, localHost, meshRudpPort, ident, syncRole);
            prs.start();
            LOG.info("AndroidRelayStack: PeerRegistrySync started advertiseHost=" + localHost
                    + " rudpPort=" + meshRudpPort
                    + " remote=" + useRemote
                    + " role=" + syncRole);

            DhtPeerDiscoverySource discoverySource = new DhtPeerDiscoverySource(btEngine);
            byte[] ownPub = (ident != null) ? ident.ed25519PubRaw() : null;
            PeerDiscovery discovery = new PeerDiscovery(discoverySource, pd,
                    new DirectTcpPeerAuthenticator(), ownPub);
            pds = new PeerDiscoveryScheduler(discovery, PEER_DISCOVERY_INTERVAL_SEC);
            pds.start();
            LOG.info("AndroidRelayStack: PeerDiscoveryScheduler started");

            int advertiseRelayPort = readConfiguredRelayPort();
            IdentityRecordPublisher identityPublisher =
                    new IdentityRecordPublisher(ident, advertiseRelayPort, meshRudpPort,
                            syncRole.name());
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
            LOG.info("AndroidRelayStack: LOCAL and DISTRIBUTED search engines wired"
                    + " LocalIndex.size=" + li.size()
                    + " remoteIceBridge=" + useRemote
                    + " meshRudp=" + meshRudpPort);

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

    /** Control client (local embedded or USE_REMOTE). May be null after close. */
    public IceBridgeClient client() {
        return client;
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

    private static int readConfiguredRudpPort() {
        return readConfiguredPort(Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT,
                PeerRegistrySync.ICEBRIDGE_RUDP_PORT);
    }

    private static int readConfiguredRelayPort() {
        return readConfiguredPort(Constants.PREF_KEY_ICEBRIDGE_RELAY_PORT,
                RelayConstants.RELAY_LISTEN_PORT);
    }

    private static int readConfiguredPort(String prefKey, int fallback) {
        try {
            ConfigurationManager cm = ConfigurationManager.instance();
            String raw = cm.getString(prefKey);
            if (raw != null && !raw.isEmpty()) {
                int p = Integer.parseInt(raw.trim());
                if (p >= 1 && p <= 65535) {
                    return p;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static IceBridgeConfig.Role readConfiguredRole() {
        try {
            String raw = ConfigurationManager.instance()
                    .getString(Constants.PREF_KEY_ICEBRIDGE_ROLE);
            if (raw != null && !raw.isEmpty()) {
                return IceBridgeConfig.Role.valueOf(raw.trim().toUpperCase(java.util.Locale.US));
            }
        } catch (Throwable ignored) {
        }
        return IceBridgeConfig.Role.BOTH;
    }

    /** icebridge-remote.txt: line1=url, line2=token (optional). */
    private static String[] readRemoteConfigLines(File file) throws java.io.IOException {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file),
                        java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                lines.add(line);
            }
        }
        return lines.toArray(new String[0]);
    }

    /**
     * Schedule {@link SharedTorrentIndexer#indexExisting} for every transfer
     * currently known to {@link TransferManager}. Safe to call multiple times.
     */
    private static void reindexExistingTransfers(SharedTorrentIndexer indexer, String phase) {
        if (indexer == null) {
            return;
        }
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
            int indexSize = -1;
            try {
                if (SearchEngine.LOCAL_WIRING.localIndex() != null) {
                    indexSize = SearchEngine.LOCAL_WIRING.localIndex().size();
                }
            } catch (Throwable ignored) {
            }
            LOG.info("AndroidRelayStack: reindex (" + phase + ") scheduled for "
                    + existing.size() + " transfer(s); LocalIndex.size=" + indexSize);
        } catch (Throwable t) {
            LOG.warn("AndroidRelayStack: reindex (" + phase + ") failed", t);
        }
    }

    private static void scheduleDelayedReindex(SharedTorrentIndexer indexer) {
        SystemUtils.postToHandlerDelayed(
                SystemUtils.HandlerThreadName.MISC,
                () -> reindexExistingTransfers(indexer, "delayed+" + DELAYED_REINDEX_MS + "ms"),
                DELAYED_REINDEX_MS);
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
        // Keep LocalIndex open if wiring still points at it for UI; only clear transport.
        try {
            SearchEngine.DISTRIBUTED_WIRING.searchTransport(null);
            SearchEngine.DISTRIBUTED_WIRING.peerDirectory(null);
        } catch (Throwable ignored) {
        }
        try { if (localIndex != null) localIndex.close(); } catch (Throwable t) { LOG.warn("Error closing localIndex", t); }
        try {
            SearchEngine.LOCAL_WIRING.localIndex(null);
            SearchEngine.DISTRIBUTED_WIRING.localIndex(null);
        } catch (Throwable ignored) {
        }
        LOG.info("AndroidRelayStack: shutdown complete");
    }
}
