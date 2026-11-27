/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.PopupMenu;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.bittorrent.PaymentOptions;
import com.frostwire.transfers.TransferState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailFragmentTest {
  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final String MAGNET = "magnet:?xt=urn:btih:" + HASH;

  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<NetworkManager> networks;
  private ConfigurationManager configuration;
  private NetworkManager network;
  private UIBittorrentDownload download;
  private FragmentTestHost activity;
  private Menu menu;
  private TransferDetailFragment fragment;

  @Before
  public void setUp() {
    repository = mockStatic(ConfigurationRepository.class);
    configurations = mockStatic(ConfigurationManager.class);
    networks = mockStatic(NetworkManager.class);
    configuration = mock(ConfigurationManager.class);
    network = mock(NetworkManager.class);
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    networks.when(NetworkManager::instance).thenReturn(network);
    download = mock(UIBittorrentDownload.class);
    when(download.getInfoHash()).thenReturn(HASH);
    when(download.getCachedMagnetUri()).thenReturn(MAGNET);
    when(download.getState()).thenReturn(TransferState.DOWNLOADING);
    when(download.getDisplayName()).thenReturn("A Torrent");
  }

  @After
  public void tearDown() {
    networks.close();
    configurations.close();
    repository.close();
  }

  private void launch(boolean withDownload) {
    fragment = new TransferDetailFragment();
    if (withDownload) {
      fragment.setUiBittorrentDownload(download);
    }
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    activity = host.get();
    menu = new PopupMenu(activity, new View(activity)).getMenu();
    activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);
    activity.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, menu);
  }

  private boolean select(int id) {
    MenuItem item = menu.findItem(id);
    assertNotNull(item);
    return activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
  }

  private static String clip() {
    Context app = ApplicationProvider.getApplicationContext();
    ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData data = cm.getPrimaryClip();
    return data == null ? null : data.getItemAt(0).getText().toString();
  }

  @Test
  public void withoutPaymentOptionsTheDonationItemsAreHidden() {
    when(download.hasPaymentOptions()).thenReturn(false);
    launch(true);
    assertFalse(menu.findItem(R.id.fragment_transfer_detail_menu_donate_fiat).isVisible());
    assertFalse(menu.findItem(R.id.fragment_transfer_detail_menu_donate_bitcoin).isVisible());
  }

  @Test
  public void eachPaymentOptionShowsItsOwnDonationItem() {
    when(download.hasPaymentOptions()).thenReturn(true);
    when(download.getPaymentOptions())
        .thenReturn(new PaymentOptions(null, "https://pay.example/me"));
    launch(true);
    assertTrue(menu.findItem(R.id.fragment_transfer_detail_menu_donate_fiat).isVisible());
    assertFalse(menu.findItem(R.id.fragment_transfer_detail_menu_donate_bitcoin).isVisible());
  }

  @Test
  public void withoutATransferTheMenuIsLeftAlone() {
    launch(false);
    assertTrue(menu.findItem(R.id.fragment_transfer_detail_menu_donate_fiat).isVisible());
  }

  @Test
  public void copyingTheMagnetAndTheInfoHashPutsThemOnTheClipboard() {
    launch(true);
    assertTrue(select(R.id.fragment_transfer_detail_menu_copy_magnet));
    assertEquals(MAGNET, clip());
    assertTrue(select(R.id.fragment_transfer_detail_menu_copy_infohash));
    assertEquals(HASH, clip());
  }

  @Test
  public void aTransferWithoutAMagnetCopiesNothing() {
    when(download.getCachedMagnetUri()).thenReturn("");
    launch(true);
    assertTrue(select(R.id.fragment_transfer_detail_menu_copy_magnet));
    assertEquals(null, clip());
  }

  @Test
  public void theDonationItemsOpenThePaymentLinks() {
    when(download.hasPaymentOptions()).thenReturn(true);
    PaymentOptions options =
        new PaymentOptions("175tWpb8K1S7NmH4Zx6rewF9WQrcZv245W", "https://pay.example/me");
    when(download.getPaymentOptions()).thenReturn(options);
    launch(true);
    Application app = ApplicationProvider.getApplicationContext();

    assertTrue(select(R.id.fragment_transfer_detail_menu_donate_fiat));
    Intent fiat = shadowOf(app).getNextStartedActivity();
    assertNotNull(fiat);
    assertEquals("https://pay.example/me", fiat.getDataString());

    assertTrue(select(R.id.fragment_transfer_detail_menu_donate_bitcoin));
    Intent bitcoin = shadowOf(app).getNextStartedActivity();
    assertNotNull(bitcoin);
    assertEquals("bitcoin:175tWpb8K1S7NmH4Zx6rewF9WQrcZv245W", bitcoin.getDataString());
  }

  @Test
  public void thePauseResumeSeedItemFollowsTheTransferState() {
    // downloading: it pauses
    when(download.isPaused()).thenReturn(false);
    launch(true);
    assertTrue(select(R.id.fragment_transfer_detail_menu_pause_resume_seed));
    verify(download).pause();
    fragment.updatePauseResumeSeedMenuAction();
  }

  @Test
  public void aPausedTransferResumesWhenThereIsANetwork() {
    when(download.isPaused()).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(true);
    when(network.isInternetDataConnectionUp()).thenReturn(true);
    launch(true);
    fragment.updatePauseResumeSeedMenuAction();
    assertTrue(select(R.id.fragment_transfer_detail_menu_pause_resume_seed));
    verify(download, never()).pause();
  }

  @Test
  public void anUnknownItemIsNotHandled() {
    launch(true);
    MenuItem other = menu.add(0, 0x7f0f9999, 0, "other");
    assertFalse(activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, other));
  }
}
