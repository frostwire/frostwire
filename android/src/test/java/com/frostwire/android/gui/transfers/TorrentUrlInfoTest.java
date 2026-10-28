/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Opening a magnet link that is cut short, or carries a base32 hash, must not throw from getHash():
 * TorrentFetcherDownload's constructor calls it (found by fuzzing TorrentUrlInfo).
 */
public class TorrentUrlInfoTest {

  @Test
  public void hexMagnetsHaveTheirHash() {
    String hash = "0123456789abcdef0123456789abcdef01234567";
    assertEquals(hash, new TorrentUrlInfo("magnet:?xt=urn:btih:" + hash.toUpperCase() + "&dn=x").getHash());
  }

  @Test
  public void shortAndBase32MagnetsHaveNoHash() {
    assertNull(new TorrentUrlInfo("magnet:?xt=urn:btih:\u0006").getHash());
    assertNull(
        new TorrentUrlInfo(
                "magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567&dn=a-long-enough-name")
            .getHash());
    assertNull(new TorrentUrlInfo("https://example.com/file.torrent").getHash());
    assertNull(new TorrentUrlInfo(null).getHash());
  }
}
