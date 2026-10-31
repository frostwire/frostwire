/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.bittorrent.BittorrentDownload.MetadataLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Adding a transfer asked for its license metadata on the EDT, which went through jlibtorrent, the
 * resume file and a bdecode of the .torrent there (Icebase #477fa2478cf3). The lookup must run on
 * the loader's executor, never on the thread asking.
 */
class MetadataLoaderTest {

  private final List<Runnable> queued = new ArrayList<>();
  private final AtomicInteger lookups = new AtomicInteger();

  @Test
  void theLookupRunsOnTheExecutorNotOnTheCaller() {
    MetadataLoader.Loaded result = new MetadataLoader.Loaded(null, null);
    MetadataLoader loader = loader(result);

    assertNull(loader.get(), "nothing is loaded yet");
    assertEquals(0, lookups.get(), "the caller must not look up the metadata itself");
    assertEquals(1, queued.size());

    queued.remove(0).run();
    assertEquals(1, lookups.get());
    assertSame(result, loader.get());
    assertTrue(queued.isEmpty(), "loaded metadata is not looked up again");
  }

  @Test
  void aRunningOrRecentLookupIsNotStartedAgain() {
    MetadataLoader loader = loader(null); // the .torrent can't be read yet

    loader.get();
    loader.get();
    loader.get();
    assertEquals(1, queued.size(), "one lookup at a time");

    queued.remove(0).run();
    loader.get();
    assertTrue(queued.isEmpty(), "an unreadable .torrent is looked up again only after the gate");
  }

  private MetadataLoader loader(MetadataLoader.Loaded result) {
    return new MetadataLoader(
        queued::add,
        () -> {
          lookups.incrementAndGet();
          return result;
        });
  }
}
