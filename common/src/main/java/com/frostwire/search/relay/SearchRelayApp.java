/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.IceBridgeAuth;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

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
 *
 * <p>The app also installs a {@code GET /catalog} fetcher (see
 * {@link com.frostwire.search.relay.icebridge.control.CatalogFetcher}) that
 * browses a peer's full shared-torrent manifest with {@link CatalogBrowser}.
 * Response rows are capped at {@link RemoteSearchRequest#MAX_LIMIT} and at a
 * {@link #MAX_CATALOG_RESPONSE_BYTES} byte budget. Large manifests rely on
 * the transport's DATA fragmentation for delivery; the relay/legacy path is
 * <b>not</b> chunked, so an over-budget catalog is truncated here rather than
 * streamed.
 */
public final class SearchRelayApp implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SearchRelayApp.class);
    private static final Gson GSON = new Gson();

    /**
     * Approximate byte budget for a single {@code GET /catalog} JSON body.
     * Mirrors the mesh payload budget; once reached, no further rows are
     * emitted. Large manifests are expected to ride transport DATA
     * fragmentation — the relay path is not chunked.
     */
    private static final int MAX_CATALOG_RESPONSE_BYTES = RemoteSearchResponse.MAX_STREAM_BYTES;

    private final IceBridgeSearchTransport transport;
    private final IncomingSearchRequestHandler handler;
    private final PeerRegistrySync registrySync;
    private final PeerDirectory directory;
    private final IceBridgeServer server;

    private SearchRelayApp(IceBridgeSearchTransport transport,
                           IncomingSearchRequestHandler handler,
                           PeerRegistrySync registrySync,
                            PeerDirectory directory,
                            IceBridgeServer server) {
        this.transport = transport;
        this.handler = handler;
        this.registrySync = registrySync;
        this.directory = directory;
        this.server = server;
    }

    public static SearchRelayApp start(IceBridgeServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server is null");
        }
        if (!server.setSharedConsumerEnabled(true)) {
            throw new IllegalStateException("server has no shared delivery queue");
        }
        IceBridgeClient client = new IceBridgeClient(server.controlPort());
        client.setAuthToken(server.authToken());
        LocalIndex emptyIndex = new EmptyLocalIndex();
        PeerDirectory directory = new PeerDirectory(new PeerKarmaCache(
                new RemoteKarmaChainFetcher(peerPub -> null)));

        IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
        transport.start();

        // Install the relay-side catalog fetch hook for GET /catalog. The
        // CatalogBrowser is single-fetch and not thread-safe, while the
        // control API may invoke the fetcher from several HTTP workers, so
        // calls are serialized on the browser instance.
        CatalogBrowser catalogBrowser = new CatalogBrowser(server.identity(), transport);
        server.setCatalogFetcher((pubB64, timeoutMs) -> fetchCatalog(catalogBrowser, pubB64, timeoutMs));

        // Install the relay-side torrent fetch hook for GET /torrent. The
        // MeshTorrentMetadataFetcher registers a temporary transport listener,
        // so concurrent HTTP workers must not interleave: calls are serialized
        // on a dedicated lock.
        Object torrentFetchLock = new Object();
        server.setTorrentFetcher((infoHashHex, holderPubB64, timeoutMs) ->
                fetchTorrent(server, transport, torrentFetchLock, infoHashHex, holderPubB64, timeoutMs));

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
        return new SearchRelayApp(transport, handler, registrySync, directory, server);
    }

    /** Visible for tests: the directory fed by registry mesh import. */
    PeerDirectory directory() {
        return directory;
    }

    /**
     * Catalog fetch hook body for {@code GET /catalog}: decode the requested
     * publisher, browse its manifest over the mesh, and flatten the rows into
     * the {@code {"ih","name","s","fc","pub"}} control-API shape.
     *
     * <p>Never throws: any failure yields an error {@link JsonObject}. The
     * {@code pub} field is the requested publisher in hex — catalog rows
     * carry no independent publisher field, so the requested key is used.
     * Rows are capped at {@link RemoteSearchRequest#MAX_LIMIT} and stop once
     * {@link #MAX_CATALOG_RESPONSE_BYTES} is reached.
     */
    private static JsonElement fetchCatalog(CatalogBrowser browser, String pubB64, int timeoutMs) {
        try {
            byte[] pub = IceBridgeAuth.decodeBase64(pubB64);
            if (pub == null || pub.length != 32) {
                return errorJson("invalid pub");
            }
            String pubHex = Hex.encode(pub);
            List<RemoteIndexFetcher.RemoteTorrentEntry> rows;
            synchronized (browser) {
                rows = browser.fetchCatalog(pub, timeoutMs);
            }
            JsonArray result = new JsonArray();
            int emitted = 0;
            long bytes = 0;
            for (RemoteIndexFetcher.RemoteTorrentEntry row : rows) {
                if (row == null) {
                    continue;
                }
                if (emitted >= RemoteSearchRequest.MAX_LIMIT
                        || bytes >= MAX_CATALOG_RESPONSE_BYTES) {
                    break;
                }
                JsonObject element = new JsonObject();
                element.addProperty("ih", row.infoHashHex());
                element.addProperty("name", row.name());
                element.addProperty("s", row.sizeBytes());
                element.addProperty("fc", row.fileCount());
                element.addProperty("pub", pubHex);
                bytes += GSON.toJson(element).getBytes(StandardCharsets.UTF_8).length;
                result.add(element);
                emitted++;
            }
            return result;
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            return errorJson(message);
        }
    }

    private static JsonObject errorJson(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }

    /**
     * Torrent fetch hook body for {@code GET /torrent}: decode the requested
     * info hash and holder key, run the verified Protocol #3 metadata fetch,
     * and return the full .torrent bytes base64-encoded.
     *
     * <p>Never throws: any failure yields an error {@link JsonObject}. Access
     * to the transport is serialized on {@code lock} because
     * {@link MeshTorrentMetadataFetcher} registers a temporary transport
     * listener for the duration of the fetch.
     */
    private static JsonElement fetchTorrent(IceBridgeServer server,
                                            IceBridgeSearchTransport transport,
                                            Object lock,
                                            String infoHashHex,
                                            String holderPubB64,
                                            int timeoutMs) {
        try {
            byte[] infoHash = Hex.decode(infoHashHex);
            if (infoHash == null || infoHash.length != 20) {
                return errorJson("invalid ih");
            }
            byte[] holderPub = IceBridgeAuth.decodeBase64(holderPubB64);
            if (holderPub == null || holderPub.length != 32) {
                return errorJson("invalid pub");
            }
            byte[] torrentBytes;
            synchronized (lock) {
                torrentBytes = MeshTorrentMetadataFetcher.fetch(
                        transport, server.identity(), holderPub, infoHash, timeoutMs);
            }
            if (torrentBytes == null || torrentBytes.length == 0) {
                return errorJson("torrent fetch failed");
            }
            JsonObject result = new JsonObject();
            result.addProperty("data_b64", Base64.getEncoder().encodeToString(torrentBytes));
            return result;
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            return errorJson(message);
        }
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
                transport.client().close();
            }
        } catch (Throwable t) {
            LOG.debug("SearchRelayApp: transport close failed", t);
        }
        if (!server.setSharedConsumerEnabled(false)) {
            LOG.debug("SearchRelayApp: pending shared messages remain owned by server until shutdown");
        }
    }
}
