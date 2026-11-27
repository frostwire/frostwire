/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.dialogtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.app.Dialog;
import android.widget.ListView;
import com.frostwire.android.R;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialog;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.DialogTestHost;
import com.frostwire.android.gui.views.ViewTestSupport;
import com.frostwire.android.libtorrent.LoopbackTorrent;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Picking the files of a real torrent: the dialog lists the torrent's own files and the transfer
 * the user starts is added to a running engine with only the picked files wanted.
 *
 * <p>Lives outside the dialogs test package because build.gradle excludes that whole package.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class HandpickedTorrentDownloadDialogRealTorrentTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private LoopbackTorrent torrent;
  private DialogTestHost host;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<TransferManager> managers;
  private MockedStatic<UIUtils> ui;
  private TransferManager manager;

  @Before
  public void setUp() throws Exception {
    ViewTestSupport.installUiThreadPlatform();
    host = new DialogTestHost();
    torrent = new LoopbackTorrent(tmp.newFolder("root").toPath());
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            });
    manager = mock(TransferManager.class);
    managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::instance).thenReturn(manager);
    ui = mockStatic(UIUtils.class, CALLS_REAL_METHODS);
  }

  @After
  public void tearDown() {
    ui.close();
    managers.close();
    system.close();
    torrent.close();
  }

  private HandpickedTorrentDownloadDialog show(String magnet) {
    HandpickedTorrentDownloadDialog dialog =
        HandpickedTorrentDownloadDialog.newInstance(
            host.activity, torrent.album, magnet, -1, false);
    host.show(dialog, "h");
    ShadowLooper.idleMainLooper();
    return dialog;
  }

  private static int rowOf(HandpickedTorrentDownloadDialog dialog, String name) {
    for (int i = 0; i < dialog.getAdapter().getCount(); i++) {
      if (name.equals(dialog.getAdapter().getItem(i).getDisplayName())) {
        return i;
      }
    }
    throw new AssertionError("no row for " + name);
  }

  @Test
  public void theDialogListsTheFilesOfTheTorrentWithTheirSizes() {
    HandpickedTorrentDownloadDialog dialog = show(null);

    assertEquals(torrent.album.name(), dialog.getDialogTitle());
    assertEquals(2, dialog.getAdapter().getCount());
    // sorted by name
    assertEquals("one.mp3", dialog.getAdapter().getItem(0).getDisplayName());
    assertEquals(40_000L, dialog.getAdapter().getItem(0).getSize());
    assertEquals("two.txt", dialog.getAdapter().getItem(1).getDisplayName());
    assertEquals(20_000L, dialog.getAdapter().getItem(1).getSize());
    // every file starts checked
    assertEquals(2, dialog.getChecked().size());
  }

  @Test
  public void theUsersPickIsAddedToTheEngineWithOnlyThosePiecesWanted() throws Exception {
    String magnet =
        "magnet:?xt=urn:btih:"
            + torrent.album.infoHashV1().toHex()
            + "&x.pe=127.0.0.1:"
            + torrent.seederEndpoint().port();
    HandpickedTorrentDownloadDialog dialog = show(magnet);
    assertEquals(magnet, dialog.getMagnetUri());
    Dialog shown = dialog.getDialog();
    ListView list = shown.findViewById(R.id.dialog_confirm_list_listview);
    // take two.txt off the list
    android.view.View row = dialog.getAdapter().getView(rowOf(dialog, "two.txt"), null, list);
    row.findViewById(R.id.view_selectable_list_item_checkbox).performClick();
    assertEquals(1, dialog.getChecked().size());

    shown.findViewById(R.id.dialog_confirm_list_button_yes).performClick();
    ShadowLooper.idleMainLooper();

    assertFalse(shown.isShowing());
    verify(manager).incrementStartedTransfers();
    TestEngine.await(() -> torrent.local.engine.find(torrent.album) != null, "the engine has it");
    TorrentHandle handle = torrent.local.engine.find(torrent.album);
    assertNotNull(handle);
    Priority[] priorities = handle.filePriorities();
    assertEquals(Priority.NORMAL, priorities[torrent.fileIndex("one.mp3")]);
    assertEquals(Priority.IGNORE, priorities[torrent.fileIndex("two.txt")]);
    // the transfer list is refreshed with the new transfer
    ArgumentCaptor<TorrentHandle> refreshed = ArgumentCaptor.forClass(TorrentHandle.class);
    verify(manager).updateUIBittorrentDownload(refreshed.capture());
    assertEquals(torrent.album.infoHashV1().toHex(), refreshed.getValue().infoHash().toHex());
    assertTrue(torrent.local.engine.find(torrent.album).isValid());
  }

  @Test
  public void startingWithoutAnyFileCheckedStartsNothing() {
    HandpickedTorrentDownloadDialog dialog = show(null);
    Dialog shown = dialog.getDialog();
    ListView list = shown.findViewById(R.id.dialog_confirm_list_listview);
    for (int i = 0; i < dialog.getAdapter().getCount(); i++) {
      dialog
          .getAdapter()
          .getView(i, null, list)
          .findViewById(R.id.view_selectable_list_item_checkbox)
          .performClick();
    }
    assertEquals(0, dialog.getChecked().size());

    shown.findViewById(R.id.dialog_confirm_list_button_yes).performClick();

    assertTrue(shown.isShowing());
    assertEquals(null, torrent.local.engine.find(torrent.album));
  }
}
