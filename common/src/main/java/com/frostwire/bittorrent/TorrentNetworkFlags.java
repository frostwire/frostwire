/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;

/**
 * The libtorrent flag that makes a torrent an I2P torrent, behind one place.
 *
 * <p>jlibtorrent 2.1.2.1 binds {@code torrent_flags::i2p_torrent} only in the generated layer
 * ({@code swig.libtorrent.getI2p_torrent()}); its {@code TorrentFlags} class has no constant for
 * it. When jlibtorrent adds {@code TorrentFlags.I2P_TORRENT}, {@link #i2pTorrent()} becomes a
 * one-line change and nothing else in FrostWire moves.
 */
final class TorrentNetworkFlags {

  private TorrentNetworkFlags() {}

  /**
   * {@code torrent_flags::i2p_torrent}: use only I2P peers unless {@code allow_i2p_mixed} is on.
   */
  static torrent_flags_t i2pTorrent() {
    // TODO: use TorrentFlags.I2P_TORRENT once jlibtorrent has it.
    return libtorrent.getI2p_torrent();
  }

  /** Whether libtorrent has the torrent flagged as an I2P torrent right now. */
  static boolean isI2pTorrent(TorrentHandle th) {
    return th.flags().and_(i2pTorrent()).nonZero();
  }
}
