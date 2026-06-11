/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-source Bitcoin block header fetcher with a local flat-file cache.
 *
 * <p>HTTP sources are tried in order, falling through on failure. A
 * 5-second timeout is applied per request. Once a block is cached,
 * it is never re-fetched (Bitcoin blocks are immutable).
 *
 * <p>The local cache is a flat file {@code <cacheDir>/block-headers.cache}
 * with one line per cached block: {@code <height> <hex-hash>}.
 *
 * <p>Thread-safe: uses a {@link ConcurrentHashMap} for the in-memory
 * cache and synchronizes file writes.
 */
public final class HttpBlockHeaderFetcher implements BlockHeaderSource {

    private static final Logger LOG = Logger.getLogger(HttpBlockHeaderFetcher.class);

    private static final int HTTP_TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "FrostWire/1.0";
    private static final String CACHE_FILE_NAME = "block-headers.cache";

    private final File cacheFile;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<Long, byte[]> cache = new ConcurrentHashMap<>();

    public HttpBlockHeaderFetcher(File cacheDir) {
        if (cacheDir == null) {
            throw new IllegalArgumentException("cacheDir is null");
        }
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IllegalStateException("Could not create cache directory: " + cacheDir);
        }
        this.cacheFile = new File(cacheDir, CACHE_FILE_NAME);
        this.httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        loadCache();
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
        if (height < 0) {
            return null;
        }
        byte[] hash = cache.get(height);
        if (hash != null) {
            return new BitcoinBlockReference(height, hash);
        }
        for (BitcoinHeaderApi api : BitcoinHeaderApi.values()) {
            try {
                String response = fetch(api.blockUrl(height));
                byte[] parsedHash = api.parseBlockHash(response);
                if (parsedHash != null) {
                    cache.put(height, parsedHash);
                    persistCache();
                    return new BitcoinBlockReference(height, parsedHash);
                }
            } catch (Throwable t) {
                LOG.debug("Block header source " + api + " failed for height " + height +
                        ": " + t.getMessage());
            }
        }
        return null;
    }

    @Override
    public long getChainTipHeight() {
        for (BitcoinHeaderApi api : BitcoinHeaderApi.values()) {
            try {
                String response = fetch(api.tipUrl);
                if (response != null) {
                    String trimmed = response.trim();
                    if (!trimmed.isEmpty()) {
                        return Long.parseLong(trimmed);
                    }
                }
            } catch (Throwable t) {
                LOG.debug("Chain tip source " + api + " failed: " + t.getMessage());
            }
        }
        return -1;
    }

    private String fetch(String url) throws IOException {
        return httpClient.get(url, HTTP_TIMEOUT_MS, USER_AGENT, null, null, null);
    }

    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new FileReader(cacheFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int space = line.indexOf(' ');
                if (space <= 0) continue;
                try {
                    long height = Long.parseLong(line.substring(0, space));
                    byte[] hash = Hex.decode(line.substring(space + 1));
                    if (hash.length == 32) {
                        cache.put(height, hash);
                    }
                } catch (Throwable t) {
                    LOG.debug("Skipping malformed cache line: " + line);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to load block header cache", e);
        }
    }

    private synchronized void persistCache() {
        try (PrintWriter writer = new PrintWriter(cacheFile, StandardCharsets.UTF_8)) {
            cache.forEach((height, hash) ->
                    writer.println(height + " " + Hex.encode(hash)));
        } catch (IOException e) {
            LOG.warn("Failed to persist block header cache", e);
        }
    }

    /**
     * Bitcoin block header HTTP sources, tried in priority order.
     */
    private enum BitcoinHeaderApi {
        BLOCKSTREAM("https://blockstream.info/api/block-height/{h}",
                "https://blockstream.info/api/blocks/tip/height",
                (resp) -> parsePlainHash(resp)),
        MEMPOOL("https://mempool.space/api/block-height/{h}",
                "https://mempool.space/api/blocks/tip/height",
                (resp) -> parsePlainHash(resp));

        final String blockUrlTemplate;
        final String tipUrl;
        final HashParser parser;

        BitcoinHeaderApi(String blockUrlTemplate, String tipUrl, HashParser parser) {
            this.blockUrlTemplate = blockUrlTemplate;
            this.tipUrl = tipUrl;
            this.parser = parser;
        }

        String blockUrl(long height) {
            return blockUrlTemplate.replace("{h}", String.valueOf(height));
        }

        byte[] parseBlockHash(String response) {
            return parser.parse(response);
        }

        private static byte[] parsePlainHash(String response) {
            if (response == null) return null;
            String trimmed = response.trim();
            if (trimmed.isEmpty() || trimmed.length() != 64) {
                return null;
            }
            try {
                return Hex.decode(trimmed);
            } catch (Throwable t) {
                return null;
            }
        }

        @FunctionalInterface
        interface HashParser {
            byte[] parse(String response);
        }
    }
}
