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
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * <p>Rate-limits per-source to prevent flood/amplification attacks. Each
 * source public key is limited to {@link #MAX_REQUESTS_PER_MINUTE} search
 * requests per minute.
 */
public final class IncomingSearchRequestHandler implements DistributedSearchTransport.PayloadListener {

    private static final Logger LOG = Logger.getLogger(IncomingSearchRequestHandler.class);

    /** Maximum incoming search requests per source per minute. */
    private static final int MAX_REQUESTS_PER_MINUTE = 30;

    private final DistributedSearchTransport transport;
    private final RelaySearchService searchService;
    private final ConcurrentHashMap<String, RateBucket> rateMap = new ConcurrentHashMap<>();

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

    public void start() {
        transport.addListener(this);
        LOG.info("IncomingSearchRequestHandler started");
    }

    public void stop() {
        transport.removeListener(this);
        LOG.info("IncomingSearchRequestHandler stopped");
    }

    @Override
    public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
        RemoteSearchRequest request = SearchPayloadCodec.decodeRequest(payload);
        if (request == null) {
            return;
        }

        // Rate-limit per source to prevent flood/amplification.
        String sourceKey = Hex.encode(sourcePub);
        if (!tryAcquire(sourceKey)) {
            LOG.debug("IncomingSearchRequestHandler: rate-limited request from " + sourceKey);
            return;
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

    private boolean tryAcquire(String sourceKey) {
        long now = System.currentTimeMillis();
        RateBucket bucket = rateMap.computeIfAbsent(sourceKey, k -> new RateBucket());
        return bucket.tryAcquire(now);
    }

    /** Simple sliding-window rate limiter per source. */
    private static final class RateBucket {
        private final long[] timestamps = new long[MAX_REQUESTS_PER_MINUTE];
        private int index = 0;

        synchronized boolean tryAcquire(long now) {
            long cutoff = now - 60_000;
            // Check if the slot at current index is older than 1 minute.
            if (timestamps[index] < cutoff) {
                timestamps[index] = now;
                index = (index + 1) % MAX_REQUESTS_PER_MINUTE;
                return true;
            }
            return false;
        }
    }
}
