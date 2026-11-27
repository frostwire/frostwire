/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.libtorrent.LoopbackTorrent;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTDownloadListener;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.mockito.MockedStatic;

/**
 * A transfer as the app lists it, made of real parts: a running engine on 127.0.0.1, the album
 * torrent (one.mp3, two.txt), a {@link BTDownload} of it and the {@link UIBittorrentDownload} that
 * wraps it. Only the transfer manager and the app's settings around it are test doubles, and the
 * settings make a finished transfer keep seeding. Close it on the thread that created it.
 */
public final class RealTransfer implements AutoCloseable {

  public final LoopbackTorrent torrent;
  public final BTDownload dl;
  public final UIBittorrentDownload ui;
  public final TransferManager manager;

  private TransferManager replacedManager;
  private final MockedStatic<TransferManager> managers;
  private final MockedStatic<ConfigurationManager> configStatic;
  private final MockedStatic<NetworkManager> networkStatic;

  private RealTransfer(
      LoopbackTorrent torrent,
      BTDownload dl,
      UIBittorrentDownload ui,
      TransferManager manager,
      MockedStatic<TransferManager> managers,
      MockedStatic<ConfigurationManager> configStatic,
      MockedStatic<NetworkManager> networkStatic) {
    this.torrent = torrent;
    this.dl = dl;
    this.ui = ui;
    this.manager = manager;
    this.managers = managers;
    this.configStatic = configStatic;
    this.networkStatic = networkStatic;
  }

  /** Adds the album to a new engine under {@code root} and wraps it. */
  public static RealTransfer start(Path root, boolean[] selection, boolean withSeeder)
      throws Exception {
    return start(root, selection, withSeeder, false);
  }

  /** {@code partialPeer}: the peer has only one file, so it stays connected to the download. */
  public static RealTransfer start(
      Path root, boolean[] selection, boolean withSeeder, boolean partialPeer) throws Exception {
    MockedStatic<TransferManager> managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::getCurrentMountAvailableBytes).thenReturn(Long.MAX_VALUE);
    ConfigurationManager config = mock(ConfigurationManager.class);
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS)).thenReturn(true);
    MockedStatic<ConfigurationManager> configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    NetworkManager network = mock(NetworkManager.class);
    when(network.isDataWIFIUp()).thenReturn(true);
    MockedStatic<NetworkManager> networkStatic = mockStatic(NetworkManager.class);
    networkStatic.when(NetworkManager::instance).thenReturn(network);
    LoopbackTorrent torrent = null;
    try {
      torrent = new LoopbackTorrent(root);
      return start(
          torrent, selection, withSeeder, partialPeer, managers, configStatic, networkStatic);
    } catch (Throwable failed) {
      if (torrent != null) {
        torrent.close();
      }
      networkStatic.close();
      configStatic.close();
      managers.close();
      throw failed;
    }
  }

  private static RealTransfer start(
      LoopbackTorrent torrent,
      boolean[] selection,
      boolean withSeeder,
      boolean partialPeer,
      MockedStatic<TransferManager> managers,
      MockedStatic<ConfigurationManager> configStatic,
      MockedStatic<NetworkManager> networkStatic)
      throws Exception {
    BTDownload dl =
        partialPeer
            ? torrent.add(selection, List.of(torrent.partialSeederEndpoint()))
            : torrent.add(selection, withSeeder);
    // The app's own listener reacts on libtorrent's thread to the app's real singletons (the
    // settings, the transfer manager), which a unit test has no instance of; this one just waits.
    dl.setListener(
        new BTDownloadListener() {
          @Override
          public void finished(BTDownload finished) {}

          @Override
          public void removed(BTDownload removed, Set<File> incompleteFiles) {}
        });
    TransferManager manager = mock(TransferManager.class);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    when(manager.getBittorrentDownload(ui.getInfoHash())).thenReturn(ui);
    // the screens ask TransferManager.instance() from background threads too, where a static mock
    // does not reach: the singleton itself is the test's manager
    TransferManager replaced = setSingleton(manager);
    RealTransfer transfer =
        new RealTransfer(torrent, dl, ui, manager, managers, configStatic, networkStatic);
    transfer.replacedManager = replaced;
    return transfer;
  }

  /** Waits until every wanted file is on disk and refreshes the wrapper. */
  public void awaitComplete() {
    TestEngine.await(
        () -> {
          dl.refreshStatusCache();
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the transfer completed");
  }

  private static TransferManager setSingleton(TransferManager manager) {
    try {
      Field field = TransferManager.class.getDeclaredField("instance");
      field.setAccessible(true);
      TransferManager replaced = (TransferManager) field.get(null);
      field.set(null, manager);
      return replaced;
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void close() {
    setSingleton(replacedManager);
    torrent.close();
    networkStatic.close();
    configStatic.close();
    managers.close();
  }
}
