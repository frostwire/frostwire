/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import com.frostwire.android.R;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Offering an endorsement of the peer a seeding torrent came from. The positive paths need a real
 * {@code BTDownload}, whose class cannot be initialised without libtorrent's native library, so
 * only the cases that decide "do not offer" run here.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class EndorseSourcePeerMenuActionTest {

  private static UIBittorrentDownload uiTorrent(TransferState state, boolean complete) {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getState()).thenReturn(state);
    when(dl.isComplete()).thenReturn(complete);
    return dl;
  }

  @Test
  public void notOfferedForTransfersThatAreNotCompleteSeedingUiTorrents() {
    assertFalse(EndorseSourcePeerMenuAction.mayOffer(mock(BittorrentDownload.class)));
    assertFalse(EndorseSourcePeerMenuAction.mayOffer(uiTorrent(TransferState.DOWNLOADING, true)));
    assertFalse(EndorseSourcePeerMenuAction.mayOffer(uiTorrent(TransferState.SEEDING, false)));
  }

  @Test
  public void notOfferedWhenTheTorrentHasNoSessionBehindIt() {
    UIBittorrentDownload noSession = uiTorrent(TransferState.SEEDING, true);
    assertNull(noSession.getDl());
    assertFalse(EndorseSourcePeerMenuAction.mayOffer(noSession));
    assertFalse(EndorseSourcePeerMenuAction.canEndorse(noSession));
  }

  @Test
  public void tappingTheItemOfANonOfferableTransferDoesNothing() {
    ActivityController<androidx.appcompat.app.AppCompatActivity> controller =
        Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    Context activity = controller.get();
    UIBittorrentDownload finished = uiTorrent(TransferState.FINISHED, true);
    try (MockedStatic<SystemUtils> system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
        MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      EndorseSourcePeerMenuAction action = new EndorseSourcePeerMenuAction(activity, finished);
      assertNotNull(action.getText());
      action.onClick();
      system.verify(
          () -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()),
          never());
      ui.verify(() -> UIUtils.showShortMessage(any(Context.class), anyInt()), never());
    }
  }
}
