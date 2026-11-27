/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.activities.SettingsActivity;
import com.frostwire.android.gui.activities.WizardActivity;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.util.UIUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** MainController is the thin bridge between the navigation menu and the main activity. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MainControllerTest {

  private static Intent startedBy(MainActivity activity) {
    ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
    verify(activity).startActivity(captor.capture());
    return captor.getValue();
  }

  @Test
  public void exposesTheActivityItWasCreatedWith() {
    MainActivity activity = mock(MainActivity.class);
    assertSame(activity, new MainController(activity).getActivity());
  }

  @Test
  public void switchFragmentSwitchesOnlyWhenTheMenuIdHasAFragment() {
    MainActivity activity = mock(MainActivity.class);
    Fragment fragment = new Fragment();
    when(activity.getFragmentByNavMenuId(R.id.menu_main_search)).thenReturn(fragment);
    MainController controller = new MainController(activity);
    controller.switchFragment(R.id.menu_main_search);
    verify(activity).switchContent(fragment);
    controller.switchFragment(R.id.menu_main_settings);
    verify(activity, org.mockito.Mockito.times(1)).switchContent(any(Fragment.class));
    assertSame(fragment, controller.getFragmentByNavMenuId(R.id.menu_main_search));
    Fragment other = new Fragment();
    controller.switchContent(other);
    verify(activity).switchContent(other);
  }

  @Test
  public void preferencesAndMusicOpenTheirActivities() {
    MainActivity activity = mock(MainActivity.class);
    MainController controller = new MainController(activity);
    controller.showPreferences();
    assertEquals(
        SettingsActivity.class.getName(), startedBy(activity).getComponent().getClassName());

    MainActivity musicHost = mock(MainActivity.class);
    new MainController(musicHost).launchMyMusic();
    Intent music = startedBy(musicHost);
    assertEquals(
        com.andrew.apollo.ui.activities.HomeActivity.class.getName(),
        music.getComponent().getClassName());
    assertTrue((music.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0);
    assertTrue((music.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }

  @Test
  public void startingTheWizardResetsTheConfigurationFirst() {
    MainActivity activity = mock(MainActivity.class);
    ConfigurationManager config = mock(ConfigurationManager.class);
    try (MockedStatic<ConfigurationManager> configs = mockStatic(ConfigurationManager.class)) {
      configs.when(ConfigurationManager::instance).thenReturn(config);
      new MainController(activity).startWizardActivity();
    }
    verify(config).resetToDefaults();
    Intent wizard = startedBy(activity);
    assertEquals(WizardActivity.class.getName(), wizard.getComponent().getClassName());
    assertTrue((wizard.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
  }

  @Test
  public void openingTheDownloadsFolderFallsBackToAMessageWhenNoAppHandlesIt() {
    MainActivity activity = mock(MainActivity.class);
    new MainController(activity).openOSFileExplorer();
    assertEquals(DownloadManager.ACTION_VIEW_DOWNLOADS, startedBy(activity).getAction());

    MainActivity noHandler = mock(MainActivity.class);
    doThrow(new ActivityNotFoundException("none")).when(noHandler).startActivity(any(Intent.class));
    when(noHandler.getString(R.string.error_cannot_open_downloads_folder))
        .thenReturn("cannot open");
    try (MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      new MainController(noHandler).openOSFileExplorer();
      ui.verify(() -> UIUtils.showShortMessage(noHandler, "cannot open"));
    }
  }

  @Test
  public void shutdownSyncAndTitleAreForwardedToTheActivity() {
    MainActivity activity = mock(MainActivity.class);
    MainController controller = new MainController(activity);
    controller.showShutdownDialog();
    controller.syncNavigationMenu();
    controller.setTitle("Transfers");
    verify(activity).showShutdownDialog();
    verify(activity).syncNavigationMenu();
    verify(activity).setTitle("Transfers");
  }

  @Test
  public void showTransfersSelectsTheStatusTabThenSwitchesAndRefreshes() {
    MainActivity activity = mock(MainActivity.class);
    TransfersFragment transfers = mock(TransfersFragment.class);
    when(activity.getFragmentByNavMenuId(R.id.menu_main_transfers)).thenReturn(transfers);
    doAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            })
        .when(activity)
        .runOnUiThread(any());
    new MainController(activity).showTransfers(TransfersFragment.TransferStatus.SEEDING);
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(transfers, activity);
    order.verify(transfers).selectStatusTab(TransfersFragment.TransferStatus.SEEDING);
    order.verify(activity).switchContent(transfers);
    order.verify(transfers).onTime();
  }

  @Test
  public void showTransfersDoesNothingWhenTheTransfersTabIsAlreadyShowing() {
    MainActivity activity = mock(MainActivity.class);
    when(activity.getCurrentFragment()).thenReturn(mock(TransfersFragment.class));
    new MainController(activity).showTransfers(TransfersFragment.TransferStatus.ALL);
    verify(activity, never()).runOnUiThread(any());
  }

  @Test
  public void everyOperationIsANoOpOnceTheActivityIsGone() {
    MainController controller = new MainController(null);
    assertNull(controller.getActivity());
    assertNull(controller.getFragmentByNavMenuId(R.id.menu_main_search));
    controller.switchFragment(R.id.menu_main_search);
    controller.showPreferences();
    controller.launchMyMusic();
    controller.openOSFileExplorer();
    controller.startWizardActivity();
    controller.showShutdownDialog();
    controller.syncNavigationMenu();
    controller.setTitle("x");
    controller.switchContent(new Fragment());
    controller.showTransfers(TransfersFragment.TransferStatus.ALL);
  }
}
