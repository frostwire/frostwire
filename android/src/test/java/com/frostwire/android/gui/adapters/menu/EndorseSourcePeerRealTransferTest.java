/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.frostwire.android.R;
import com.frostwire.android.gui.AndroidSearchEngines;
import com.frostwire.android.gui.transfers.RealTransfer;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.relay.KarmaChainWriter;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.util.Hex;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

/**
 * Endorsing the peer a seeding torrent came from, over a real finished transfer: its source peer is
 * kept in the torrent's resume data and its v1 info hash comes from libtorrent.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class EndorseSourcePeerRealTransferTest {

  private static final byte[] PEER = new byte[32];

  static {
    for (int i = 0; i < PEER.length; i++) {
      PEER[i] = (byte) (i + 1);
    }
  }

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private RealTransfer transfer;
  private KarmaChainWriter writer;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<AndroidSearchEngines> engines;
  private MockedStatic<UIUtils> ui;
  private AppCompatActivity activity;

  @Before
  public void setUp() throws Exception {
    transfer = RealTransfer.start(tmp.newFolder("root").toPath(), null, true);
    transfer.awaitComplete();
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(0)).run();
              return null;
            });
    writer = mock(KarmaChainWriter.class);
    RelaySearchWiring wiring = mock(RelaySearchWiring.class);
    when(wiring.karmaChainWriter()).thenReturn(writer);
    engines = mockStatic(AndroidSearchEngines.class);
    engines.when(AndroidSearchEngines::distributed).thenReturn(wiring);
    ui = mockStatic(UIUtils.class);
    ActivityController<AppCompatActivity> controller =
        Robolectric.buildActivity(AppCompatActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    activity = controller.get();
  }

  @After
  public void tearDown() {
    ui.close();
    engines.close();
    system.close();
    transfer.close();
  }

  private String v1Hash() {
    return transfer.dl.getV1InfoHash();
  }

  @Test
  public void aSeedingTransferWithAVerifiedSourceCanBeOfferedAndEndorsed() {
    assertTrue(transfer.dl.setDistributedSourcePeerPub(PEER));
    when(writer.canEndorseSourcePeer(any(), any())).thenReturn(true);
    when(writer.endorseSourcePeer(any(), any())).thenReturn(true);
    assertTrue(EndorseSourcePeerMenuAction.mayOffer(transfer.ui));
    assertTrue(EndorseSourcePeerMenuAction.canEndorse(transfer.ui));

    new EndorseSourcePeerMenuAction(activity, transfer.ui).onClick();
    shadowOf(Looper.getMainLooper()).idle();

    AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
    assertTrue(dialog.isShowing());
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();

    ArgumentCaptor<byte[]> hash = ArgumentCaptor.forClass(byte[].class);
    ArgumentCaptor<byte[]> peer = ArgumentCaptor.forClass(byte[].class);
    verify(writer).endorseSourcePeer(peer.capture(), hash.capture());
    assertArrayEquals(PEER, peer.getValue());
    assertEquals(v1Hash(), Hex.encode(hash.getValue()));
    ui.verify(() -> UIUtils.showShortMessage(activity, R.string.endorse_source_peer_success));
  }

  @Test
  public void aWriterThatRefusesTheEndorsementShowsTheFailure() {
    transfer.dl.setDistributedSourcePeerPub(PEER);
    when(writer.canEndorseSourcePeer(any(), any())).thenReturn(true);
    when(writer.endorseSourcePeer(any(), any())).thenReturn(false);

    new EndorseSourcePeerMenuAction(activity, transfer.ui).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    ((AlertDialog) ShadowDialog.getLatestDialog())
        .getButton(DialogInterface.BUTTON_POSITIVE)
        .performClick();
    shadowOf(Looper.getMainLooper()).idle();

    ui.verify(() -> UIUtils.showShortMessage(activity, R.string.endorse_source_peer_unavailable));
  }

  @Test
  public void cancellingTheConfirmationEndorsesNothing() {
    transfer.dl.setDistributedSourcePeerPub(PEER);
    when(writer.canEndorseSourcePeer(any(), any())).thenReturn(true);

    new EndorseSourcePeerMenuAction(activity, transfer.ui).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    ((AlertDialog) ShadowDialog.getLatestDialog())
        .getButton(DialogInterface.BUTTON_NEGATIVE)
        .performClick();
    shadowOf(Looper.getMainLooper()).idle();

    verify(writer, never()).endorseSourcePeer(any(), any());
  }

  @Test
  public void aSourceTheChainWillNotEndorseIsReportedUnavailable() {
    transfer.dl.setDistributedSourcePeerPub(PEER);
    when(writer.canEndorseSourcePeer(any(), any())).thenReturn(false);
    assertFalse(EndorseSourcePeerMenuAction.canEndorse(transfer.ui));

    new EndorseSourcePeerMenuAction(activity, transfer.ui).onClick();
    shadowOf(Looper.getMainLooper()).idle();

    ui.verify(() -> UIUtils.showShortMessage(activity, R.string.endorse_source_peer_unavailable));
    verify(writer, never()).endorseSourcePeer(any(), any());
  }

  @Test
  public void aTransferThatDidNotComeFromAVerifiedPeerIsNotOffered() {
    // no source peer was recorded for this transfer
    assertFalse(EndorseSourcePeerMenuAction.mayOffer(transfer.ui));
    new EndorseSourcePeerMenuAction(activity, transfer.ui).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    verify(writer, never()).canEndorseSourcePeer(any(), any());
    ui.verify(() -> UIUtils.showShortMessage(any(Context.class), anyInt()), never());
  }

  @Test
  public void withoutAKarmaChainWriterNothingIsOffered() {
    transfer.dl.setDistributedSourcePeerPub(PEER);
    RelaySearchWiring noChain = mock(RelaySearchWiring.class);
    engines.when(AndroidSearchEngines::distributed).thenReturn(noChain);
    assertFalse(EndorseSourcePeerMenuAction.mayOffer(transfer.ui));
    assertFalse(EndorseSourcePeerMenuAction.canEndorse(transfer.ui));
  }

  @Test
  public void aFinishingActivityGetsNoDialog() {
    transfer.dl.setDistributedSourcePeerPub(PEER);
    when(writer.canEndorseSourcePeer(any(), any())).thenReturn(true);
    activity.finish();

    new EndorseSourcePeerMenuAction(activity, transfer.ui).onClick();
    shadowOf(Looper.getMainLooper()).idle();

    assertTrue(
        ShadowDialog.getLatestDialog() == null || !ShadowDialog.getLatestDialog().isShowing());
    ui.verify(() -> UIUtils.showShortMessage(any(Context.class), anyInt()), never());
  }
}
