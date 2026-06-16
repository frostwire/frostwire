/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.util.Logger;

import java.util.Optional;

/**
 * Listens for incoming search requests on a {@link DistributedSearchTransport}
 * and dispatches them to the local {@link RelaySearchService}.
 *
 * <p>Registered as a permanent listener on the transport. When a payload
 * arrives that decodes as a valid {@link RemoteSearchRequest}, the handler
 * processes it through the search service, signs the response, and sends it
 * back to the requester via the transport. Payloads that do not decode as
 * requests (e.g. responses to our own searches) are silently ignored — the
 * {@link com.frostwire.search.relay.DistributedSearchPerformer}'s transient
 * listener handles those.
 */
public final class IncomingSearchRequestHandler implements DistributedSearchTransport.PayloadListener {

    private static final Logger LOG = Logger.getLogger(IncomingSearchRequestHandler.class);

    private final DistributedSearchTransport transport;
    private final RelaySearchService searchService;

    public IncomingSearchRequestHandler(DistributedSearchTransport transport,
                                        RelaySearchService searchService) {
        if (transport == null) {
            throw new IllegalArgumentException("transport is null");
        }
        if (searchService == null) {
            throw new IllegalArgumentException("searchService is null");
        }
        this.transport = transport;
        this.searchService = searchService;
    }

    /**
     * Register this handler as a permanent listener on the transport.
     */
    public void start() {
        transport.addListener(this);
        LOG.info("IncomingSearchRequestHandler started");
    }

    /**
     * Remove this handler from the transport.
     */
    public void stop() {
        transport.removeListener(this);
        LOG.info("IncomingSearchRequestHandler stopped");
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
        RemoteSearchRequest request = SearchPayloadCodec.decodeRequest(payload);
        if (request == null) {
            return; // not a search request — probably a response
        }
        try {
            Optional<RemoteSearchResponse> response = searchService.handle(request);
            if (response.isEmpty()) {
                return;
            }
            byte[] responseBytes = SearchPayloadCodec.encodeResponse(response.get());
            transport.send(request.requesterPub(), responseBytes);
        } catch (Throwable t) {
            LOG.debug("IncomingSearchRequestHandler failed to process request", t);
        }
    }
}
