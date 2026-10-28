/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes Android's {@link TorrentUrlInfo}, which wraps the torrent URL or magnet link a user opens
 * (links from web pages, other apps and the clipboard, through TransferManager). Its getters declare
 * no exceptions, and TorrentFetcherDownload's constructor calls {@code getHash()}, so any exception
 * is a finding; a hash that isn't 40 lowercase hex digits is one too, since it is later decoded as
 * hex.
 */
class TorrentUrlInfoFuzzTest {

  @FuzzTest
  void getters(FuzzedDataProvider data) {
    String displayName = data.consumeBoolean() ? data.consumeString(64) : null;
    TorrentUrlInfo info = new TorrentUrlInfo(data.consumeRemainingAsString(), displayName);
    info.getDisplayName();
    String hash = info.getHash();
    if (hash != null && !hash.matches("[0-9a-f]{40}")) {
      throw new IllegalStateException("getHash() returned a non-hex hash: " + hash);
    }
  }
}
