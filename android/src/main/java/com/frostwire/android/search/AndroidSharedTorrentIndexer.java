/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexResult;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.SharedTorrentIndexer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Owns bounded indexing work; a queued callback never grants sharing authorization. */
final class AndroidSharedTorrentIndexer extends ShareIndexQueue<BTDownload>
    implements BTEngineListener {

  AndroidSharedTorrentIndexer(LocalIndex index, IdentityKeys identity, BooleanSupplier permitted) {
    super(
        index,
        dl ->
            permitted.getAsBoolean()
                && AndroidShareVisibility.isLiveTransfer(dl.getInfoHash(), dl)
                && permitted.getAsBoolean(),
        dl -> {
          IndexResult result =
              new SharedTorrentIndexer(index, identity)
                  .indexTorrentInfo(dl.getTorrentHandle().torrentFile(), dl.getName());
          if (result != IndexResult.UPSERTED) {
            throw new IllegalStateException("Torrent indexing failed: " + result);
          }
        },
        BTDownload::getInfoHash,
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64),
            runnable -> new Thread(runnable, "AndroidShareIndexer")));
  }

  AndroidSharedTorrentIndexer(
      LocalIndex index,
      Predicate<BTDownload> eligible,
      Consumer<BTDownload> indexDownload,
      Executor executor) {
    super(index, eligible, indexDownload, BTDownload::getInfoHash, executor, (task, delay) -> {});
  }

  @Override
  public void started(BTEngine engine) {}

  @Override
  public void stopped(BTEngine engine) {}

  @Override
  public void downloadAdded(BTEngine engine, BTDownload download) {
    submit(download);
  }

  @Override
  public void downloadUpdate(BTEngine engine, BTDownload download) {
    submit(download);
  }
}
