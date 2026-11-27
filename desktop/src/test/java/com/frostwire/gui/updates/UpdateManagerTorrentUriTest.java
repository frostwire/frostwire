/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Which torrent URLs of an update message the update manager opens. */
class UpdateManagerTorrentUriTest {

  @Test
  void httpAndHttpsTorrentUrlsAreOpened() {
    assertNotNull(UpdateManager.torrentUri("http://example.com/a.torrent"));
    assertEquals(
        "https://example.com/a.torrent",
        UpdateManager.torrentUri("https://example.com/a.torrent").toString());
    assertNotNull(UpdateManager.torrentUri("HTTPS://example.com/a.torrent"));
  }

  @Test
  void otherSchemesAndBrokenUrlsAreIgnored() {
    assertNull(UpdateManager.torrentUri("ftp://example.com/a.torrent"));
    assertNull(UpdateManager.torrentUri("file:///tmp/a.torrent"));
    assertNull(UpdateManager.torrentUri("example.com/a.torrent"));
    assertNull(UpdateManager.torrentUri("https:///a.torrent"));
    assertNull(UpdateManager.torrentUri("https://exa mple.com/a.torrent"));
  }
}
