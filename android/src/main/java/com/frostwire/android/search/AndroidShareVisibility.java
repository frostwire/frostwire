/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Public authorization, independent of local history/search preferences. Call off main (JNI). */
public final class AndroidShareVisibility implements ShareVisibilityPolicy {
  private final BooleanSupplier participating;
  private final Predicate<String> shared;

  public AndroidShareVisibility(BooleanSupplier participating) {
    this(
        participating,
        hash ->
            isLiveTransfer(
                hash,
                null,
                TransferManager.instance()::getBittorrentDownload,
                TransferManager.instance()::getTransfers));
  }

  AndroidShareVisibility(BooleanSupplier participating, Predicate<String> shared) {
    this.participating = participating;
    this.shared = shared;
  }

  @Override
  public boolean isVisible(String hash) {
    try {
      return hash != null
          && participating.getAsBoolean()
          && shared.test(hash.toLowerCase(Locale.ROOT))
          && participating.getAsBoolean();
    } catch (Throwable unavailable) {
      return false;
    }
  }

  static boolean isLiveTransfer(String hash, BTDownload expected) {
    return isLiveTransfer(
        hash,
        expected,
        TransferManager.instance()::getBittorrentDownload,
        TransferManager.instance()::getTransfers);
  }

  static boolean isLiveTransfer(
      String hash, BTDownload expected, Function<String, BittorrentDownload> lookup) {
    return isLiveTransfer(hash, expected, lookup, List::of);
  }

  static boolean isLiveTransfer(
      String hash,
      BTDownload expected,
      Function<String, BittorrentDownload> lookup,
      Supplier<? extends List<? extends Transfer>> transfers) {
    BittorrentDownload transfer = resolve(hash, lookup, transfers.get());
    if (!(transfer instanceof UIBittorrentDownload)) {
      return false;
    }
    UIBittorrentDownload ui = (UIBittorrentDownload) transfer;
    BTDownload download = ui.getDl();
    if (download == null
        || ui.isRemovedFromSharing()
        || ui.isSharingPaused()
        || (expected != null && download != expected)
        || download.wasPaused()
        || download.isPaused()) {
      return false;
    }
    TorrentHandle handle = download.getTorrentHandle();
    if (handle == null || !handle.isValid()) {
      return false;
    }
    TorrentInfo info = handle.torrentFile();
    return info != null
        && info.isValid()
        && !info.isPrivate()
        && !ui.isRemovedFromSharing()
        && !ui.isSharingPaused()
        && !download.wasPaused()
        && !download.isPaused()
        && resolve(hash, lookup, transfers.get()) == ui;
  }

  private static BittorrentDownload resolve(
      String hash,
      Function<String, BittorrentDownload> lookup,
      List<? extends Transfer> transfers) {
    return resolve(hash, lookup, transfers, AndroidShareVisibility::v1Hash);
  }

  static BittorrentDownload resolve(
      String hash,
      Function<String, BittorrentDownload> lookup,
      List<? extends Transfer> transfers,
      Function<UIBittorrentDownload, String> v1Hash) {
    BittorrentDownload direct = lookup.apply(hash);
    if (direct != null) {
      return direct;
    }
    // Hybrid torrents have distinct v1 and v2 hashes. The live transfer map uses
    // TorrentHandle.infoHash(), while the shared index publishes TorrentInfo.infoHashV1().
    for (Transfer transfer : transfers) {
      if (!(transfer instanceof UIBittorrentDownload)) {
        continue;
      }
      String v1 = v1Hash.apply((UIBittorrentDownload) transfer);
      if (v1 != null && v1.equalsIgnoreCase(hash)) {
        return (BittorrentDownload) transfer;
      }
    }
    return null;
  }

  private static String v1Hash(UIBittorrentDownload ui) {
    try {
      BTDownload download = ui.getDl();
      if (download == null) {
        return null;
      }
      TorrentHandle handle = download.getTorrentHandle();
      if (handle == null || !handle.isValid()) {
        return null;
      }
      TorrentInfo info = handle.torrentFile();
      if (info == null || !info.isValid()) {
        return null;
      }
      Sha1Hash v1 = info.infoHashV1();
      return v1 == null ? null : v1.toHex();
    } catch (RuntimeException unavailable) {
      return null;
    }
  }
}
