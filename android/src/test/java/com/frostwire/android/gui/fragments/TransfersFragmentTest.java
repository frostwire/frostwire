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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.VPNStatusDetailActivity;
import com.frostwire.android.gui.fragments.preference.TorrentPreferenceFragment;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.TransferUpdateExecutor;
import com.frostwire.android.gui.views.ClearableEditTextView;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransfersFragmentTest {
  private MockedStatic<TransferManager> transferManagers;
  private MockedStatic<NetworkManager> networkManagers;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<Engine> engines;
  private TransferManager transferManager;
  private NetworkManager networkManager;
  private ConfigurationManager configuration;
  private Engine engine;
  private final List<Transfer> transfers = new ArrayList<>();

  @Before
  public void setUp() {
    TestTransfersFragment.engineStarted = false;
    transferManager = mock(TransferManager.class);
    networkManager = mock(NetworkManager.class);
    configuration = mock(ConfigurationManager.class);
    engine = mock(Engine.class);
    transferManagers = mockStatic(TransferManager.class);
    networkManagers = mockStatic(NetworkManager.class);
    configurations = mockStatic(ConfigurationManager.class);
    engines = mockStatic(Engine.class);
    transferManagers.when(TransferManager::instance).thenReturn(transferManager);
    networkManagers.when(NetworkManager::instance).thenReturn(networkManager);
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    engines.when(Engine::instance).thenReturn(engine);
    when(transferManager.getTransfers()).thenReturn(transfers);
    when(configuration.isSeedFinishedTorrents()).thenReturn(true);
  }

  @After
  public void tearDown() {
    transferManagers.close();
    networkManagers.close();
    configurations.close();
    engines.close();
  }

  private static Transfer transfer(String name, TransferState state, long created) {
    Transfer t = mock(Transfer.class);
    when(t.getDisplayName()).thenReturn(name);
    when(t.getState()).thenReturn(state);
    when(t.getCreated()).thenReturn(new Date(created));
    return t;
  }

  private static TabLayout tabs(TransfersFragment f) {
    return f.requireView().findViewById(R.id.fragment_transfers_layout_tab_layout);
  }

  private static RecyclerView list(TransfersFragment f) {
    return f.requireView().findViewById(R.id.fragment_transfers_list);
  }

  private static String text(TransfersFragment f, int id) {
    return ((TextView) f.requireView().findViewById(id)).getText().toString();
  }

  private static ClearableEditTextView urlBox(TransfersFragment f) {
    return f.requireView().findViewById(R.id.fragment_transfers_add_transfer_text_input);
  }

  private static TestTransfersFragment launch() {
    TestTransfersFragment fragment = new TestTransfersFragment();
    FragmentTestHost.launch(fragment);
    return fragment;
  }

  @Test
  public void theHeaderIsTitledTransfers() {
    TestTransfersFragment fragment = new TestTransfersFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    View header = fragment.getHeader(host.get());
    assertEquals(
        fragment.getString(R.string.transfers),
        ((TextView) header.findViewById(R.id.view_transfers_header_text_title))
            .getText()
            .toString());
  }

  @Test
  public void startsOnTheAllTabWithNoNoSeedsNotice() {
    TestTransfersFragment fragment = launch();
    assertEquals(0, tabs(fragment).getSelectedTabPosition());
    assertEquals(
        View.GONE,
        fragment.requireView().findViewById(R.id.fragment_transfers_no_seeds_view).getVisibility());
    assertEquals(
        View.GONE,
        fragment
            .requireView()
            .findViewById(R.id.fragment_transfers_vpn_notification)
            .getVisibility());
  }

  @Test
  public void swipingMovesBetweenTabsAndWrapsAround() {
    TestTransfersFragment fragment = launch();
    fragment.selectStatusTabToThe(true);
    assertEquals(1, tabs(fragment).getSelectedTabPosition());
    fragment.selectStatusTabToThe(false);
    assertEquals(0, tabs(fragment).getSelectedTabPosition());
    // left of the first tab is the last one
    fragment.selectStatusTabToThe(false);
    assertEquals(3, tabs(fragment).getSelectedTabPosition());
    // and right of the last one is the first
    fragment.selectStatusTabToThe(true);
    assertEquals(0, tabs(fragment).getSelectedTabPosition());
  }

  @Test
  public void eachTabListsOnlyItsTransfers() throws Exception {
    transfers.add(transfer("downloading", TransferState.DOWNLOADING, 3));
    transfers.add(transfer("paused", TransferState.PAUSED, 2));
    transfers.add(transfer("seeding", TransferState.SEEDING, 5));
    transfers.add(transfer("finished", TransferState.FINISHED, 4));
    transfers.add(transfer("complete", TransferState.COMPLETE, 1));
    TestTransfersFragment fragment = launch();
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter() != null);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getItemCount() == 5);
    assertEquals(5, list(fragment).getAdapter().getItemCount());

    fragment.selectStatusTab(TransfersFragment.TransferStatus.DOWNLOADING);
    fragment.onTime(true);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getItemCount() == 2);
    assertEquals(2, list(fragment).getAdapter().getItemCount());

    fragment.selectStatusTab(TransfersFragment.TransferStatus.SEEDING);
    fragment.onTime(true);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getItemCount() == 1);
    assertEquals(1, list(fragment).getAdapter().getItemCount());

    fragment.selectStatusTab(TransfersFragment.TransferStatus.COMPLETED);
    fragment.onTime(true);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getItemCount() == 2);
    assertEquals(2, list(fragment).getAdapter().getItemCount());

    fragment.selectStatusTab(TransfersFragment.TransferStatus.ALL);
    fragment.onTime(true);
    FragmentTestHost.settleUntil(() -> list(fragment).getAdapter().getItemCount() == 5);
    assertEquals(5, list(fragment).getAdapter().getItemCount());
  }

  @Test
  public void theSeedingTabSuggestsWhatToDoWhenNothingSeeds() {
    transfers.add(transfer("finished", TransferState.FINISHED, 4));
    TestTransfersFragment fragment = launch();
    View notice = fragment.requireView().findViewById(R.id.fragment_transfers_no_seeds_view);

    // seeding turned off: ask to enable it
    when(configuration.isSeedFinishedTorrents()).thenReturn(false);
    tabs(fragment).selectTab(tabs(fragment).getTabAt(2));
    fragment.onTime(true);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(View.VISIBLE, notice.getVisibility());
    assertEquals(
        fragment.getString(R.string.seeding_is_currently_disabled),
        text(fragment, R.id.view_transfers_no_seeds_top_textview));

    // seeding on, something finished and nothing seeding: offer to seed it all
    when(configuration.isSeedFinishedTorrents()).thenReturn(true);
    fragment.onTime(true);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(
        fragment.getString(R.string.nothing_is_currently_seeding),
        text(fragment, R.id.view_transfers_no_seeds_top_textview));

    // something is seeding: no notice
    transfers.add(transfer("seeding", TransferState.SEEDING, 5));
    fragment.onTime(true);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(View.GONE, notice.getVisibility());

    // no transfers at all: no notice either
    transfers.clear();
    fragment.onTime(true);
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(View.GONE, notice.getVisibility());
  }

  @Test
  public void theMenuShowsOnlyWhatAppliesAndActsOnTheTransfers() {
    TestTransfersFragment fragment = new TestTransfersFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    FragmentTestHost activity = host.get();
    Menu menu = new PopupMenu(activity, new View(activity)).getMenu();
    activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);
    activity.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, null, menu);
    // nothing was ever listed: everything but "add" is hidden
    assertFalse(menu.findItem(R.id.fragment_transfers_menu_clear_all).isVisible());
    assertFalse(menu.findItem(R.id.fragment_transfers_menu_pause_stop_all).isVisible());
    assertFalse(menu.findItem(R.id.fragment_transfers_menu_resume_all).isVisible());
    assertFalse(menu.findItem(R.id.fragment_transfers_menu_seed_all).isVisible());
    assertFalse(menu.findItem(R.id.fragment_transfers_menu_stop_seeding_all).isVisible());

    select(activity, menu, R.id.fragment_transfers_menu_clear_all);
    verify(transferManager).clearComplete();

    select(activity, menu, R.id.fragment_transfers_menu_pause_stop_all);
    verify(transferManager).stopHttpTransfers();
    verify(transferManager).pauseTorrents();

    select(activity, menu, R.id.fragment_transfers_menu_stop_seeding_all);
    verify(transferManager).stopSeedingTorrents();

    // resume, bittorrent disconnected
    when(transferManager.isBittorrentDisconnected()).thenReturn(true);
    select(activity, menu, R.id.fragment_transfers_menu_resume_all);
    assertEquals(
        fragment.getString(R.string.cant_resume_torrent_transfers),
        ShadowToast.getTextOfLatestToast());
    verify(transferManager, never()).resumeResumableTransfers();

    // resume, connected but no internet
    when(transferManager.isBittorrentDisconnected()).thenReturn(false);
    when(networkManager.isInternetDataConnectionUp()).thenReturn(false);
    select(activity, menu, R.id.fragment_transfers_menu_resume_all);
    assertEquals(
        fragment.getString(R.string.please_check_connection_status_before_resuming_download),
        ShadowToast.getTextOfLatestToast());
    verify(transferManager, never()).resumeResumableTransfers();

    // resume, all good
    when(networkManager.isInternetDataConnectionUp()).thenReturn(true);
    select(activity, menu, R.id.fragment_transfers_menu_resume_all);
    verify(transferManager).resumeResumableTransfers();

    // add transfer shows the url box and a second time hides it
    assertEquals(View.GONE, urlBox(fragment).getVisibility());
    select(activity, menu, R.id.fragment_transfers_menu_add_transfer);
    assertEquals(View.VISIBLE, urlBox(fragment).getVisibility());
    select(activity, menu, R.id.fragment_transfers_menu_add_transfer);
    assertEquals(View.GONE, urlBox(fragment).getVisibility());
  }

  private static boolean select(FragmentTestHost activity, Menu menu, int id) {
    MenuItem item = menu.findItem(id);
    assertNotNull(item);
    return activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, item);
  }

  @Test
  public void aTransferUrlIsStartedFromTheUrlBox() {
    TestTransfersFragment fragment = launch();
    ClearableEditTextView box = urlBox(fragment);
    box.setVisibility(View.VISIBLE);

    // nothing valid typed
    box.setText("not a link");
    enter(box);
    assertEquals(
        fragment.getString(R.string.please_enter_valid_url), ShadowToast.getTextOfLatestToast());
    verify(transferManager, never()).downloadTorrent(anyString(), any());

    // a magnet link starts a torrent download
    String magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=hi";
    box.setVisibility(View.VISIBLE);
    box.setText(magnet);
    enter(box);
    verify(transferManager).downloadTorrent(eq(magnet), any());
    assertEquals(
        fragment.getString(R.string.torrent_url_added), ShadowToast.getTextOfLatestToast());
    assertEquals(View.GONE, box.getVisibility());
    assertEquals("", box.getText());

    // a web page link that isn't a torrent or a cloud one needs the search screen: not here
    box.setVisibility(View.VISIBLE);
    box.setText("https://example.com/video");
    enter(box);
    assertEquals(View.GONE, box.getVisibility());
  }

  private static void enter(ClearableEditTextView box) {
    box.getAutoCompleteTextView()
        .dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void theStatusIconsOpenTheirScreens() {
    TestTransfersFragment fragment = launch();
    Application app = ApplicationProvider.getApplicationContext();

    fragment.requireView().findViewById(R.id.fragment_transfers_dht_peers).performClick();
    Intent settings = shadowOf(app).getNextStartedActivity();
    assertNotNull(settings);
    assertEquals(SettingsActivity.class.getName(), settings.getComponent().getClassName());

    fragment.requireView().findViewById(R.id.fragment_transfers_status_vpn_icon).performClick();
    Intent vpn = shadowOf(app).getNextStartedActivity();
    assertNotNull(vpn);
    assertEquals(VPNStatusDetailActivity.class.getName(), vpn.getComponent().getClassName());
    assertEquals(Constants.ACTION_SHOW_VPN_STATUS_UNPROTECTED, vpn.getAction());
    assertEquals("transfers", vpn.getStringExtra("from"));

    // the rich toast goes away when tapped
    View toast = fragment.requireView().findViewById(R.id.fragment_transfers_vpn_notification);
    toast.setVisibility(View.VISIBLE);
    toast.performClick();
    assertEquals(View.GONE, toast.getVisibility());
  }

  @Test
  public void showingTheFragmentWithoutAVpnShowsTheRichToastOnlyTheFirstTime() {
    TestTransfersFragment fragment = launch();
    View toast = fragment.requireView().findViewById(R.id.fragment_transfers_vpn_notification);
    when(networkManager.isTunnelUp()).thenReturn(false);
    fragment.onShow();
    assertEquals(View.VISIBLE, toast.getVisibility());
    toast.setVisibility(View.GONE);
    fragment.onShow();
    assertEquals(View.GONE, toast.getVisibility());
  }

  @Test
  public void theSelectedTabSurvivesARecreate() {
    TestTransfersFragment fragment = new TestTransfersFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    tabs(fragment).selectTab(tabs(fragment).getTabAt(1));
    host.recreate();
    TestTransfersFragment restored = null;
    for (Fragment f : host.get().getSupportFragmentManager().getFragments()) {
      if (f instanceof TestTransfersFragment) {
        restored = (TestTransfersFragment) f;
      }
    }
    assertNotNull(restored);
    // the next timer tick marks the restored status' tab
    restored.onTime(true);
    assertEquals(1, tabs(restored).getSelectedTabPosition());
  }

  @Test
  public void pausingAndDestroyingTheViewReleasesTheList() {
    TestTransfersFragment fragment = new TestTransfersFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    host.pause();
    host.resume();
    host.pause().stop().destroy();
    assertNull(fragment.getView());
  }

  @Test
  public void withTheEngineStartedTheStatusBarAndDhtLineReflectTheNetwork() {
    TestTransfersFragment.engineStarted = true;
    when(transferManager.getActiveDownloads()).thenReturn(2);
    when(transferManager.getActiveUploads()).thenReturn(1);
    when(networkManager.isTunnelUp()).thenReturn(true);
    when(networkManager.isInternetDataConnectionUp()).thenReturn(true);
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    TestTransfersFragment fragment = launch();
    shadowOf(Looper.getMainLooper()).idle();

    // the first refresh paints the status bar
    assertTrue(text(fragment, R.id.fragment_transfers_text_downloads).startsWith("2 @ "));
    assertTrue(text(fragment, R.id.fragment_transfers_text_uploads).startsWith("1 @ "));
    ImageView vpn = fragment.requireView().findViewById(R.id.fragment_transfers_status_vpn_icon);
    assertEquals(R.drawable.notification_vpn_on, shadowOf(vpn.getDrawable()).getCreatedFromResId());

    TextView dht = fragment.requireView().findViewById(R.id.fragment_transfers_dht_peers);
    // no DHT known: hidden
    when(engine.isStopped()).thenReturn(false);
    fragment.onTime(true);
    assertEquals(View.INVISIBLE, dht.getVisibility());

    when(engine.isStopped()).thenReturn(true);
    fragment.onTime(true);
    assertEquals(View.VISIBLE, dht.getVisibility());
    assertEquals(fragment.getString(R.string.bittorrent_off), dht.getText().toString());
    // and then tapping it goes to the main settings
    dht.performClick();
    Intent settings =
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity();
    assertNull(settings.getStringExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT));

    when(engine.isStopped()).thenReturn(false);
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY))
        .thenReturn(true);
    when(networkManager.isTunnelUp()).thenReturn(false);
    when(networkManager.isVpnConnected()).thenReturn(false);
    fragment.onTime(true);
    assertEquals(
        fragment.getString(R.string.bittorrent_off_vpn_required), dht.getText().toString());

    when(transferManager.isMobileAndDataSavingsOn()).thenReturn(true);
    fragment.onTime(true);
    assertEquals(
        fragment.getString(R.string.bittorrent_off_data_saver_on), dht.getText().toString());

    when(networkManager.isInternetDataConnectionUp()).thenReturn(false);
    fragment.onTime(true);
    assertEquals(fragment.getString(R.string.check_internet_connection), dht.getText().toString());
    dht.performClick();
    Intent torrentSettings =
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity();
    assertEquals(
        TorrentPreferenceFragment.class.getName(),
        torrentSettings.getStringExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT));
  }

  /** The list's refreshes run on the calling thread, where the test's mocks are. */
  public static class TestTransfersFragment extends TransfersFragment {
    static volatile boolean engineStarted;

    @Override
    boolean isBittorrentEngineStarted() {
      return engineStarted;
    }

    @Override
    TransferUpdateExecutor createTransferUpdateExecutor() {
      return new TransferUpdateExecutor() {
        @Override
        public void initialize() {}

        @Override
        public boolean execute(Runnable task) {
          task.run();
          return true;
        }

        @Override
        public boolean isAvailable() {
          return true;
        }

        @Override
        public void shutdown() {}
      };
    }
  }
}
