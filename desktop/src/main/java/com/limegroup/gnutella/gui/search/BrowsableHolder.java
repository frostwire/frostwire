/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.LibTorrentMagnetDownloader;
import com.frostwire.search.relay.DistributedSearchPerformer;

/**
 * Decides whether a search result advertises a catalog the user can browse: it came from
 * Distributed Search, its holder is known and the holder opted in to sharing its catalog (the
 * {@code x.hc} flag on the result magnet). Results that do not qualify get no browse action.
 */
public final class BrowsableHolder {

  private BrowsableHolder() {}

  /** The holder's key when the result advertises a browsable catalog, otherwise null. */
  public static byte[] advertisedPub(String source, String magnet) {
    if (!DistributedSearchPerformer.SOURCE_NAME.equals(source)
        || !LibTorrentMagnetDownloader.hasPublicCatalogFlag(magnet)) {
      return null;
    }
    return LibTorrentMagnetDownloader.parseHolderPub(magnet);
  }
}
