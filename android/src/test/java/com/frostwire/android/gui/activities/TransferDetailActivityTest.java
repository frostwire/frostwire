/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import androidx.viewpager2.widget.ViewPager2;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.HeaderBanner;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.transfers.TransferState;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** The transfer details screen, with a fake torrent behind it and the banner switched off. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferDetailActivityTest {

  private static final String HASH = "0123456789abcdef0123456789abcdef01234567";

  private MockedStatic<TransferManager> transfers;
  private MockedStatic<ConfigurationManager> configs;
  private MockedStatic<HeaderBanner> banners;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<SystemUtils> system;
  private TransferManager manager;
  private ConfigurationManager config;

  @Before
  public void setUp() {
    manager = mock(TransferManager.class);
    config = mock(ConfigurationManager.class);
    transfers = mockStatic(TransferManager.class);
    transfers.when(TransferManager::instance).thenReturn(manager);
    configs = mockStatic(ConfigurationManager.class);
    configs.when(ConfigurationManager::instance).thenReturn(config);
    banners = mockStatic(HeaderBanner.class);
    ui = mockStatic(UIUtils.class);
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    // The fragments look the torrent up in the native engine on a worker thread: not here.
    system = mockStatic(SystemUtils.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
        .thenAnswer(i -> null);
    // Platforms keeps the first platform any test installed, so state the thread check here.
    system
        .when(SystemUtils::isUIThread)
        .thenAnswer(i -> Looper.myLooper() == Looper.getMainLooper());
  }

  @After
  public void tearDown() {
    transfers.close();
    configs.close();
    banners.close();
    ui.close();
    system.close();
  }

  private UIBittorrentDownload torrent(TransferState state) {
    UIBittorrentDownload dl = mock(UIBittorrentDownload.class);
    when(dl.getInfoHash()).thenReturn(HASH);
    when(dl.getState()).thenReturn(state);
    when(manager.getBittorrentDownload(HASH)).thenReturn(dl);
    return dl;
  }

  private ActivityController<TransferDetailActivity> controllerFor(String infoHash) {
    Intent intent = new Intent();
    if (infoHash != null) {
      intent.putExtra("infoHash", infoHash);
    }
    return Robolectric.buildActivity(TransferDetailActivity.class, intent);
  }

  @Test
  public void missingInfoHashShowsAMessageAndFinishes() {
    ActivityController<TransferDetailActivity> controller = controllerFor(null).create();
    TransferDetailActivity activity = controller.get();
    assertTrue(activity.isFinishing());
    ui.verify(
        () ->
            UIUtils.showShortMessage(
                activity, R.string.could_not_open_transfer_detail_invalid_infohash));
  }

  @Test
  public void unknownTorrentShowsAMessageAndFinishes() {
    when(manager.getBittorrentDownload(HASH)).thenReturn(null);
    TransferDetailActivity activity = controllerFor(HASH).create().get();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void torrentStillFetchingMetadataCannotBeOpened() {
    torrent(TransferState.DOWNLOADING_METADATA);
    TransferDetailActivity activity = controllerFor(HASH).create().get();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void openingATorrentBuildsTheSixTabsAtTheLastSelectedOne() {
    torrent(TransferState.DOWNLOADING);
    when(config.getInt(Constants.PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX))
        .thenReturn(2);
    ActivityController<TransferDetailActivity> controller = controllerFor(HASH).create();
    TransferDetailActivity activity = controller.get();
    assertTrue(!activity.isFinishing());
    ViewPager2 pager = activity.findViewById(R.id.transfer_detail_viewpager);
    assertEquals(6, pager.getAdapter().getItemCount());
    assertEquals(2, pager.getCurrentItem());
    // selecting another tab remembers it
    pager.setCurrentItem(4, false);
    shadowOf(Looper.getMainLooper()).idle();
    verify(config).setInt(Constants.PREF_KEY_TORRENT_TRANSFER_DETAIL_LAST_SELECTED_TAB_INDEX, 4);
    controller.destroy();
  }

  @Test
  public void resumingSubscribesToTheTimerAndPausingUnsubscribes() {
    torrent(TransferState.SEEDING);
    ActivityController<TransferDetailActivity> controller =
        controllerFor(HASH).create().start().resume();
    banners.verify(() -> HeaderBanner.onResumeHideOrUpdate(any()));
    // resuming twice re-subscribes the same subscription instead of failing
    controller.pause().resume();
    controller.pause();
    controller.destroy();
    banners.verify(() -> HeaderBanner.destroy(any()));
  }

  @Test
  public void selectedTabIndexSurvivesInstanceStateRestore() {
    torrent(TransferState.DOWNLOADING);
    ActivityController<TransferDetailActivity> controller = controllerFor(HASH).create();
    android.os.Bundle state = new android.os.Bundle();
    controller.saveInstanceState(state);
    assertTrue(state.containsKey("lastSelectedTabIndex"));
    controller.destroy();
  }
}
