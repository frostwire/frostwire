/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.tpb.TPBSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TPBSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(TPBSearchPerformerTest.class);
    private static final int MIRROR_HEAD_TIMEOUT_MS = 1500;
    private static final int PERFORMER_TIMEOUT_MS = 5000;
    private static final long TEST_TOKEN = 1337L;
    private static final String TEST_KEYWORDS = "free book";

    @Test
    public void testTPBSearch() {
        final List<TPBSearchResult> tpbResults = new ArrayList<>();
        TPBSearchPerformer tpbSearchPerformer = initializeSearchPerformer();
        assertNotNull(tpbSearchPerformer, "[TPBSearchPerformerTest] Failed to initialize search performer");
        CountDownLatch latch = new CountDownLatch(1);

        tpbSearchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("[TPBSearchPerformerTest] onResults() " + results.size());
                try {
                    for (SearchResult r : results) {
                        TPBSearchResult sr = (TPBSearchResult) r;
                        LOG.info("[TPBSearchPerformerTest] onResults() size = " + sr.getSize());
                        LOG.info("[TPBSearchPerformerTest] onResults() hash = " + sr.getHash());
                        LOG.info("[TPBSearchPerformerTest] ==== ");
                        tpbResults.add(sr);
                    }
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onError(long token, SearchError error) {
                latch.countDown();
            }

            @Override
            public void onStopped(long token) {
                latch.countDown();
            }
        });

        tpbSearchPerformer.perform();
        boolean completed = false;
        try {
            LOG.info("[TPBSearchPerformerTest] Waiting for results...");
            completed = latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("[TPBSearchPerformerTest] Error waiting for results: " + e.getMessage());
            fail("[TPBSearchPerformerTest] Interrupted while waiting for results");
        }
        assertTrue(completed, "[TPBSearchPerformerTest] Timed out waiting for results from TPB performer");
        LOG.info("[TPBSearchPerformerTest] Results found: " + tpbResults.size() + " using domain: " + tpbSearchPerformer.getDomainName());
        assertFalse(tpbResults.isEmpty(), "[TPBSearchPerformerTest] No results found using domain: " + tpbSearchPerformer.getDomainName());
    }

    private TPBSearchPerformer initializeSearchPerformer() {
        MirrorStatus fastestMirror = selectFastestMirror();
        return new TPBSearchPerformer(fastestMirror.domain, TEST_TOKEN, TEST_KEYWORDS, PERFORMER_TIMEOUT_MS);
    }

    private MirrorStatus selectFastestMirror() {
        HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
        String[] mirrors = TPBSearchPerformer.getMirrors();
        List<MirrorStatus> statuses = new ArrayList<>(mirrors.length);
        for (String mirror : mirrors) {
            statuses.add(checkMirror(httpClient, mirror, MIRROR_HEAD_TIMEOUT_MS));
        }

        List<MirrorStatus> onlineMirrors = statuses.stream()
                .filter(MirrorStatus::isOnline)
                .collect(Collectors.toList());

        if (onlineMirrors.isEmpty()) {
            String snapshot = statuses.stream()
                    .map(this::formatMirrorStatus)
                    .collect(Collectors.joining(", "));
            fail("[TPBSearchPerformerTest] No TPB mirrors responded successfully. Snapshot: " + snapshot);
        }

        List<MirrorStatus> offlineMirrors = statuses.stream()
                .filter(status -> !status.isOnline())
                .collect(Collectors.toList());
        if (!offlineMirrors.isEmpty()) {
            String offlineSummary = offlineMirrors.stream()
                    .map(this::formatMirrorStatus)
                    .collect(Collectors.joining(", "));
            LOG.warn("[TPBSearchPerformerTest] Offline mirrors detected: " + offlineSummary);
        }

        MirrorStatus fastest = onlineMirrors.stream()
                .min(Comparator.comparingLong(MirrorStatus::getResponseTimeMs))
                .orElseThrow();

        LOG.info("[TPBSearchPerformerTest] Fastest mirror: " + fastest.domain +
                " (HTTP " + fastest.statusCode + " in " + fastest.responseTimeMs + "ms)");
        return fastest;
    }

    private MirrorStatus checkMirror(HttpClient httpClient, String mirror, int timeoutMs) {
        long start = System.currentTimeMillis();
        int statusCode = -1;
        boolean online = false;
        String errorMessage = null;
        try {
            statusCode = httpClient.head("https://" + mirror, timeoutMs, null);
            online = statusCode >= 200 && statusCode < 400;
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        long duration = System.currentTimeMillis() - start;

        if (online) {
            LOG.info("[TPBSearchPerformerTest] Mirror " + mirror + " responded with HTTP " + statusCode +
                    " in " + duration + "ms");
        } else {
            String reason = statusCode > 0 ? ("HTTP " + statusCode) : (errorMessage != null ? errorMessage : "no response");
            LOG.warn("[TPBSearchPerformerTest] Mirror " + mirror + " failed (" + reason + ") after " + duration + "ms");
        }

        return new MirrorStatus(mirror, online, duration, statusCode, errorMessage);
    }

    private String formatMirrorStatus(MirrorStatus status) {
        StringBuilder builder = new StringBuilder(status.domain);
        builder.append(" -> ");
        if (status.isOnline()) {
            builder.append("HTTP ").append(status.statusCode)
                    .append(" in ").append(status.responseTimeMs).append("ms");
        } else if (status.statusCode > 0) {
            builder.append("HTTP ").append(status.statusCode);
        } else if (status.errorMessage != null) {
            builder.append(status.errorMessage);
        } else {
            builder.append("no response");
        }
        return builder.toString();
    }

    private static final class MirrorStatus {
        private final String domain;
        private final boolean online;
        private final long responseTimeMs;
        private final int statusCode;
        private final String errorMessage;

        private MirrorStatus(String domain, boolean online, long responseTimeMs, int statusCode, String errorMessage) {
            this.domain = domain;
            this.online = online;
            this.responseTimeMs = responseTimeMs;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }

        private boolean isOnline() {
            return online;
        }

        private long getResponseTimeMs() {
            return responseTimeMs;
        }
    }
}
