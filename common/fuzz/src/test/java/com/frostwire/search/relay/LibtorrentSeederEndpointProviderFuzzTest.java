/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.HashSet;
import java.util.List;

/**
 * Fuzzes {@code LibtorrentSeederEndpointProvider.parseXpe}, which splits a magnet's {@code
 * &x.pe=host:port&x.pe=...} peer parameters, and {@code buildEndpoints}, which turns the entries
 * into the seeder endpoints this node advertises to mesh peers (wildcard listen addresses replaced
 * by the LAN addresses, at most {@link LibtorrentSeederEndpointProvider#MAX_ENDPOINTS}). Only the
 * static, package-private parsers run: no jlibtorrent session and no native code.
 *
 * <p>Where the input comes from: in production, {@code BTEngine.magnetPeers()} of our own
 * libtorrent session (external address and listen endpoints), so it is shaped by what libtorrent
 * reports, not typed by a remote peer; the same {@code x.pe} syntax also arrives in magnet links
 * from anywhere. The output goes to remote peers.
 *
 * <p>Neither method documents an exception, so anything thrown is a bug, as is a broken promise of
 * {@code buildEndpoints}: more than {@code MAX_ENDPOINTS} endpoints, an empty or duplicate entry,
 * or a {@code parseXpe} entry that is empty, untrimmed or over 256 characters.
 */
class LibtorrentSeederEndpointProviderFuzzTest {

  private static final List<String> LAN = List.of("10.0.0.2", "192.168.1.5");

  @FuzzTest
  void parseXpe(FuzzedDataProvider data) {
    int sessionListenPort = data.consumeBoolean() ? 6881 : -1;
    String params = data.consumeRemainingAsString();
    List<String> entries = LibtorrentSeederEndpointProvider.parseXpe(params);
    for (String entry : entries) {
      if (entry.isEmpty() || entry.length() > 256 || !entry.equals(entry.trim())) {
        throw new IllegalStateException("parseXpe kept entry '" + entry + "'");
      }
    }
    List<String> endpoints =
        LibtorrentSeederEndpointProvider.buildEndpoints(entries, LAN, sessionListenPort);
    if (endpoints.size() > LibtorrentSeederEndpointProvider.MAX_ENDPOINTS) {
      throw new IllegalStateException(endpoints.size() + " endpoints: " + endpoints);
    }
    if (new HashSet<>(endpoints).size() != endpoints.size() || endpoints.contains("")) {
      throw new IllegalStateException("duplicate or empty endpoint: " + endpoints);
    }
  }
}
