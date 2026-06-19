/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Search performer that reads from the local {@link LocalIndex} and
 * surfaces matching rows as FrostWire search results.
 *
 * <p>This is the user-facing "Local" search engine: it queries the
 * {@code shared_torrents} FTS5 table populated by
 * {@link SharedTorrentIndexer} and returns each row as a magnet-bearing
 * {@link CompositeFileSearchResult}. It performs no network I/O and
 * reports itself as a non-crawler performer so {@code SearchManager}
 * routes it to the single-page executor.
 *
 * <p><b>Karma weighting (optional):</b> if a {@link PeerKarmaCache}
 * is supplied at construction, results are sorted by the publisher's
 * karma score (descending). Ties keep the index's natural order
 * (FTS5 rank). The first lookup for an unknown peer blocks on a
 * DHT fetch; subsequent lookups are O(1) from the cache. Pass
 * {@code null} for the karma cache to preserve the original
 * FTS5-only ordering.
 *
 * <p>Source label: {@link #SOURCE_NAME}.
 */
public final class LocalSharedTorrentSearchPerformer implements ISearchPerformer {

    public static final String SOURCE_NAME = "Local";
    static final int DEFAULT_RESULT_LIMIT = 50;

    private static final Logger LOG = Logger.getLogger(LocalSharedTorrentSearchPerformer.class);

    private final long token;
    private final String keywords;
    private final LocalIndex index;
    private final PeerKarmaCache karmaCache;
    private final int limit;

    private boolean stopped;
    private SearchListener listener;

    public LocalSharedTorrentSearchPerformer(long token, String keywords, LocalIndex index) {
        this(token, keywords, index, null, DEFAULT_RESULT_LIMIT);
    }

    public LocalSharedTorrentSearchPerformer(long token, String keywords,
                                             LocalIndex index, int limit) {
        this(token, keywords, index, null, limit);
    }

    public LocalSharedTorrentSearchPerformer(long token, String keywords,
                                             LocalIndex index,
                                             PeerKarmaCache karmaCache) {
        this(token, keywords, index, karmaCache, DEFAULT_RESULT_LIMIT);
    }

    public LocalSharedTorrentSearchPerformer(long token, String keywords,
                                             LocalIndex index,
                                             PeerKarmaCache karmaCache,
                                             int limit) {
        if (token < 0) {
            throw new IllegalArgumentException("token must be >= 0");
        }
        if (keywords == null) {
            throw new IllegalArgumentException("keywords is null");
        }
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        this.token = token;
        this.keywords = keywords;
        this.index = index;
        this.karmaCache = karmaCache;
        this.limit = limit;
    }

    @Override
    public long getToken() {
        return token;
    }

    public String getKeywords() {
        return keywords;
    }

    @Override
    public void perform() {
        SearchListener l;
        List<FileSearchResult> results = Collections.emptyList();
        try {
            l = listener;
            if (stopped || l == null) {
                return;
            }
            results = queryIndex();
            if (stopped) {
                return;
            }
            List<SearchResult> widened = new ArrayList<>(results.size());
            widened.addAll(results);
            l.onResults(token, widened);
        } catch (Throwable t) {
            LOG.warn("LocalSharedTorrentSearchPerformer failed for token " + token, t);
            if (listener != null && !stopped) {
                listener.onError(token, new SearchError(-1, t.getMessage()));
            }
        }
    }

    @Override
    public void crawl(com.frostwire.search.CrawlableSearchResult sr) {
        // Local results already carry their file metadata; nothing to crawl.
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            if (listener != null) {
                listener.onStopped(token);
            }
        } catch (Throwable t) {
            LOG.warn("Error stopping local search: " + t.getMessage());
        }
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public SearchListener getListener() {
        return listener;
    }

    @Override
    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return false;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    private List<FileSearchResult> queryIndex() {
        List<LocalSharedTorrent> rows = index.search(keywords, limit);
        if (karmaCache == null) {
            List<FileSearchResult> out = new ArrayList<>(rows.size());
            for (LocalSharedTorrent row : rows) {
                out.add(toResult(row));
            }
            return out;
        }
        // Karma-weighted sort. Stable: ties keep FTS5 rank order.
        List<LocalSharedTorrent> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            long kb = karmaFor(b);
            long ka = karmaFor(a);
            return Long.compare(kb, ka);
        });
        List<FileSearchResult> out = new ArrayList<>(sorted.size());
        for (LocalSharedTorrent row : sorted) {
            out.add(toResult(row));
        }
        return out;
    }

    private long karmaFor(LocalSharedTorrent t) {
        try {
            byte[] pub = t.publisherEd25519Pub();
            if (pub == null || pub.length != 32) {
                return 0;
            }
            return karmaCache.getKarma(pub);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    static CompositeFileSearchResult toResult(LocalSharedTorrent t) {
        return toResult(t, SOURCE_NAME);
    }

    static CompositeFileSearchResult toResult(LocalSharedTorrent t, String source) {
        String name = t.name();
        String infoHashHex = t.infoHashHex();
        long size = t.sizeBytes();
        String magnet = UrlUtils.buildMagnetUrl(infoHashHex, name, DefaultTrackers.MAGNET_URL_PARAMETERS);
        String detailsUrl = magnet;
        String matched = t.matchedFile();
        String filename = (matched != null && !matched.isEmpty())
                ? matched
                : name + ".torrent";
        return CompositeFileSearchResult.builder()
                .displayName(name)
                .filename(filename)
                .size(size)
                .detailsUrl(detailsUrl)
                .source(source)
                .creationTime(t.addedAt() * 1000L)
                .preliminary(false)
                .torrent(magnet, infoHashHex, 0, magnet)
                .build();
    }
}
