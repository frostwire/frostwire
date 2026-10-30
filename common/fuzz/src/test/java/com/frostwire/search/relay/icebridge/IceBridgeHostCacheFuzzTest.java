/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.HostCachePeerDiscoverySource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;

/**
 * Fuzzes the IceBridge host cache file: {@link IceBridgeHostCache} loading a fuzzed {@code
 * icebridge_host_cache.txt} ({@code host:port,ROLE,lastSuccessfulPingMs,consecutiveFailures} per
 * line, parsed by {@code parseLine}) from a temp file, then the reads discovery does with the
 * entries ({@link HostCachePeerDiscoverySource#fetchEndpoints()}, {@link
 * IceBridgeHostCache#getPingable(long)}).
 *
 * <p>Who writes it: FrostWire itself, in {@code ~/.frostwire} (desktop) or the app's libtorrent
 * directory (Android); a user can edit it and a crash or full disk can corrupt it. Low priority: no
 * remote input reaches it.
 *
 * <p>{@code load()} documents that a broken file or line is skipped (it catches {@code Exception}
 * and so does {@code parseLine}), so only an {@code Error} (stack or heap) or an input that takes
 * seconds can escape it; anything thrown by the reads after a successful load is a bug.
 */
class IceBridgeHostCacheFuzzTest {

  private static final File CACHE_FILE = tempCacheFile();

  @FuzzTest
  void load(FuzzedDataProvider data) throws IOException {
    Files.write(CACHE_FILE.toPath(), data.consumeRemainingAsBytes());
    IceBridgeHostCache cache = new IceBridgeHostCache(CACHE_FILE);
    new HostCachePeerDiscoverySource(cache, Collections.emptyList()).fetchEndpoints();
    cache.getPingable(0);
    cache.getPingable(60_000);
  }

  private static File tempCacheFile() {
    try {
      File dir = Files.createTempDirectory("icebridge-host-cache-fuzz").toFile();
      dir.deleteOnExit();
      File file = new File(dir, "icebridge_host_cache.txt");
      file.deleteOnExit();
      return file;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
