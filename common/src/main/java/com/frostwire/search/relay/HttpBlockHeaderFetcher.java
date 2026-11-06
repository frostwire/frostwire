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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bitcoin block hash fetcher that asks two independent block explorers and accepts only what both
 * report, with a local flat-file cache.
 *
 * <p>Every source is asked, with a 5-second timeout per request. A block hash is returned only when
 * all sources return the same hash for the height, and the tip only when all sources answer with
 * tips at most {@link #MAX_TIP_SPREAD_BLOCKS} apart (the lower one is returned). A source that is
 * down, answers garbage, or disagrees yields null or -1, so callers write nothing and try again
 * later instead of trusting one explorer alone (IceBridge white paper §11.3). Once a block is
 * cached, it is never re-fetched (Bitcoin blocks are immutable).
 *
 * <p>The local cache is a flat file {@code <cacheDir>/block-headers.cache} with one line per cached
 * block: {@code <height> <hex-hash>}.
 *
 * <p>Thread-safe: uses a {@link ConcurrentHashMap} for the in-memory cache and synchronizes file
 * writes.
 */
public final class HttpBlockHeaderFetcher implements BlockHeaderSource {

  private static final Logger LOG = Logger.getLogger(HttpBlockHeaderFetcher.class);

  private static final int HTTP_TIMEOUT_MS = 5000;
  private static final String USER_AGENT = "FrostWire/1.0";
  private static final String CACHE_FILE_NAME = "block-headers.cache";

  /**
   * How far apart the sources' tips may be. Two up-to-date explorers differ by a block for a few
   * seconds after one is found; a larger gap means one is stale or wrong.
   */
  static final int MAX_TIP_SPREAD_BLOCKS = 2;

  private final File cacheFile;
  private final UrlReader urlReader;
  private final ConcurrentHashMap<Long, byte[]> cache = new ConcurrentHashMap<>();

  public HttpBlockHeaderFetcher(File cacheDir) {
    this(cacheDir, httpReader(HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC)));
  }

  /** Reads the sources through {@code urlReader}; tests pass fake explorers. */
  HttpBlockHeaderFetcher(File cacheDir, UrlReader urlReader) {
    if (cacheDir == null) {
      throw new IllegalArgumentException("cacheDir is null");
    }
    if (urlReader == null) {
      throw new IllegalArgumentException("urlReader is null");
    }
    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
      throw new IllegalStateException("Could not create cache directory: " + cacheDir);
    }
    this.cacheFile = new File(cacheDir, CACHE_FILE_NAME);
    this.urlReader = urlReader;
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
    byte[] agreed = null;
    for (BitcoinHeaderApi api : BitcoinHeaderApi.values()) {
      byte[] parsedHash;
      try {
        parsedHash = api.parseBlockHash(urlReader.get(api.blockUrl(height)));
      } catch (Throwable t) {
        parsedHash = null;
        LOG.debug(
            "Block header source " + api + " failed for height " + height + ": " + t.getMessage());
      }
      if (parsedHash == null) {
        return null;
      }
      if (agreed == null) {
        agreed = parsedHash;
      } else if (!Arrays.equals(agreed, parsedHash)) {
        LOG.info(
            "Bitcoin block sources disagree on the hash at height " + height + ", not using it");
        return null;
      }
    }
    cache.put(height, agreed);
    persistCache();
    return new BitcoinBlockReference(height, agreed);
  }

  @Override
  public long getChainTipHeight() {
    long agreed = -1;
    for (BitcoinHeaderApi api : BitcoinHeaderApi.values()) {
      long tip;
      try {
        tip = parseTip(urlReader.get(api.tipUrl));
      } catch (Throwable t) {
        tip = -1;
        LOG.debug("Chain tip source " + api + " failed: " + t.getMessage());
      }
      if (tip < 0) {
        return -1;
      }
      if (agreed < 0) {
        agreed = tip;
      } else if (Math.abs(tip - agreed) > MAX_TIP_SPREAD_BLOCKS) {
        LOG.info("Bitcoin block sources disagree on the tip (" + agreed + ", " + tip + ")");
        return -1;
      } else {
        agreed = Math.min(agreed, tip);
      }
    }
    return agreed;
  }

  /** A tip response: a non-negative decimal height, or -1 if the response isn't one. */
  private static long parseTip(String response) {
    if (response == null) {
      return -1;
    }
    try {
      long tip = Long.parseLong(response.trim());
      return tip >= 0 ? tip : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static UrlReader httpReader(HttpClient httpClient) {
    return url -> httpClient.get(url, HTTP_TIMEOUT_MS, USER_AGENT, null, null, null);
  }

  /** Reads the body of a GET request. */
  @FunctionalInterface
  interface UrlReader {
    String get(String url) throws IOException;
  }

  private void loadCache() {
    if (!cacheFile.exists()) {
      return;
    }
    // Not FileReader(File, Charset): Android only has it from API 33 (#1400).
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8))) {
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
    // Not PrintWriter(File, Charset): Android only has it from API 33 (#1400).
    try (PrintWriter writer =
        new PrintWriter(
            new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8))) {
      cache.forEach((height, hash) -> writer.println(height + " " + Hex.encode(hash)));
    } catch (IOException e) {
      LOG.warn("Failed to persist block header cache", e);
    }
  }

  /**
   * Parses a block-height response of the Esplora API (Blockstream, mempool.space): the block hash
   * as 64 hex digits. Package-private so the fuzz harness can reach it.
   *
   * @return the 32-byte hash, or null if the response isn't one
   */
  static byte[] parsePlainHash(String response) {
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

  /** Bitcoin block header HTTP sources; all must agree. */
  private enum BitcoinHeaderApi {
    BLOCKSTREAM(
        "https://blockstream.info/api/block-height/{h}",
        "https://blockstream.info/api/blocks/tip/height",
        HttpBlockHeaderFetcher::parsePlainHash),
    MEMPOOL(
        "https://mempool.space/api/block-height/{h}",
        "https://mempool.space/api/blocks/tip/height",
        HttpBlockHeaderFetcher::parsePlainHash);

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

    @FunctionalInterface
    interface HashParser {
      byte[] parse(String response);
    }
  }
}
