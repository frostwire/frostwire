/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.HexFormat;

/**
 * Fuzzes {@link CatalogBrowser#decodeAndVerifyManifest}, which decodes the JSON catalog manifest a
 * browsed peer (or any relay forwarding for it) sends back on the CATALOG mesh protocol, bounds it,
 * and checks its Ed25519 signature ({@code verifyManifestSignature}) against the peer's key.
 *
 * <p>The peer key and request nonce are fixed so the committed seeds (v1 manifests, and both pages
 * of a v2 paged manifest, signed by that key) get past the signature check and the fuzzer starts
 * from manifests that are accepted.
 *
 * <p>The method documents a {@code null} result for anything that is not a valid manifest signed by
 * the peer, and catches {@code Throwable} itself, so an exception thrown under it never reaches
 * this harness. Findings are therefore: an input that takes seconds (a hang), and a non-null result
 * that breaks the contract {@code fetchCatalog} relies on (null rows, more rows than {@link
 * RemoteSearchRequest#MAX_LIMIT}, or a page position outside {@code 0 <= page < pages <= }{@link
 * CatalogManifestPages#MAX_PAGES}).
 */
class CatalogBrowserFuzzTest {

  private static final byte[] PEER_PUB =
      HexFormat.of().parseHex("2a73ad1f6aa883b9db943ac20acb24adc531ad55b66cf0d64e8f332727adddd6");
  private static final byte[] NONCE =
      HexFormat.of().parseHex("a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf");

  @FuzzTest
  void decodeAndVerifyManifest(FuzzedDataProvider data) {
    CatalogBrowser.Page page =
        CatalogBrowser.decodeAndVerifyManifest(data.consumeRemainingAsBytes(), NONCE, PEER_PUB);
    if (page == null) {
      return;
    }
    if (page.entries == null || page.entries.size() > RemoteSearchRequest.MAX_LIMIT) {
      throw new AssertionError("accepted manifest has invalid rows: " + page.entries);
    }
    if (page.pages < 1
        || page.pages > CatalogManifestPages.MAX_PAGES
        || page.page < 0
        || page.page >= page.pages) {
      throw new AssertionError("accepted manifest has page " + page.page + "/" + page.pages);
    }
  }
}
