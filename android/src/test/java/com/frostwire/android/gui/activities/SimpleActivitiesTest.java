/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** The small static-content activities: VPN status, About and the settings host. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SimpleActivitiesTest {

  private static VPNStatusDetailActivity vpn(String action, String from) {
    Intent intent = new Intent();
    if (action != null) {
      intent.setAction(action);
    }
    if (from != null) {
      intent.putExtra("from", from);
    }
    return Robolectric.buildActivity(VPNStatusDetailActivity.class, intent)
        .create()
        .start()
        .resume()
        .get();
  }

  @Test
  public void protectedConnectionShowsTheProtectedHeader() {
    VPNStatusDetailActivity activity = vpn(Constants.ACTION_SHOW_VPN_STATUS_PROTECTED, null);
    assertEquals(
        activity.getString(R.string.protected_connection),
        ((TextView) activity.findViewById(R.id.view_vpn_status_header)).getText().toString());
    assertNotNull(activity.findViewById(R.id.view_vpn_status_header_icon));
  }

  @Test
  public void unprotectedConnectionShowsARedUnprotectedHeader() {
    VPNStatusDetailActivity activity = vpn(null, null);
    TextView header = activity.findViewById(R.id.view_vpn_status_header);
    assertEquals(activity.getString(R.string.unprotected_connection), header.getText().toString());
    assertEquals(Color.RED, header.getCurrentTextColor());
  }

  @Test
  public void vpnLinksOpenTheProviderAndLearnMorePages() {
    VPNStatusDetailActivity activity = vpn(null, null);
    try (MockedStatic<UIUtils> ui = mockStatic(UIUtils.class)) {
      ((Button) activity.findViewById(R.id.view_vpn_status_pia)).performClick();
      ui.verify(() -> UIUtils.openURL(activity, Constants.PIA_VPN_URL));
      for (int id :
          new int[] {
            R.id.view_vpn_status_learn_more_textview,
            R.id.view_vpn_status_header,
            R.id.view_vpn_status_header_icon
          }) {
        ui.clearInvocations();
        activity.findViewById(id).performClick();
        ui.verify(() -> UIUtils.openURL(activity, Constants.FROSTWIRE_VPN_URL));
      }
    }
  }

  @Test
  public void backFromVpnDetailReturnsToMainClearingTheStack() {
    VPNStatusDetailActivity activity = vpn(null, null);
    activity.getOnBackPressedDispatcher().onBackPressed();
    Intent next = shadowOf(activity).getNextStartedActivity();
    assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    assertTrue((next.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
    assertEquals(null, next.getAction());
    assertTrue(activity.isFinishing());
  }

  @Test
  public void backFromVpnDetailOpenedFromTransfersReturnsToTheTransfersTab() {
    VPNStatusDetailActivity activity = vpn(null, "transfers");
    activity.getOnBackPressedDispatcher().onBackPressed();
    Intent next = shadowOf(activity).getNextStartedActivity();
    assertEquals(Constants.ACTION_SHOW_TRANSFERS, next.getAction());
  }

  @Test
  public void homeButtonActsAsBackAndOtherItemsAreLeftToTheSuperclass() {
    VPNStatusDetailActivity activity = vpn(null, null);
    MenuItem home = mock(MenuItem.class);
    when(home.getItemId()).thenReturn(android.R.id.home);
    assertTrue(activity.onOptionsItemSelected(home));
    assertTrue(activity.isFinishing());
    assertNotNull(shadowOf(activity).getNextStartedActivity());
  }

  @Test
  public void aboutActivityInflatesItsLayout() {
    AboutActivity activity = Robolectric.buildActivity(AboutActivity.class).create().get();
    assertNotNull(activity.findViewById(android.R.id.content));
  }

  @Test
  public void settingsHostShowsTheRequestedFragmentWithArgumentsAndTitle() {
    Intent intent = new Intent();
    intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, Fragment.class.getName());
    Bundle args = new Bundle();
    args.putString("k", "v");
    intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
    intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE, "My Title");
    ActivityController<SettingsActivity> controller =
        Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
    SettingsActivity activity = controller.get();
    shadowOf(Looper.getMainLooper()).idle();
    Fragment shown =
        activity.getSupportFragmentManager().findFragmentById(R.id.activity_settings_content);
    assertNotNull(shown);
    assertEquals(Fragment.class, shown.getClass());
    assertEquals("v", shown.getArguments().getString("k"));
    assertEquals("My Title", activity.getTitle().toString());
  }

  @Test
  public void settingsHostRejectsAFragmentClassThatDoesNotExist() {
    Intent intent = new Intent();
    intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, "no.such.Fragment");
    try {
      Robolectric.buildActivity(SettingsActivity.class, intent).create();
      org.junit.Assert.fail("expected a RuntimeException");
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("no.such.Fragment"));
    }
  }

  @Test
  public void startingAPreferenceSubScreenLaunchesTheHostAgainWithThatFragment() {
    Intent intent = new Intent();
    intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, Fragment.class.getName());
    SettingsActivity activity =
        Robolectric.buildActivity(SettingsActivity.class, intent).create().get();
    Preference pref = mock(Preference.class);
    when(pref.getFragment()).thenReturn("some.Sub.Fragment");
    when(pref.getTitle()).thenReturn("Sub");
    when(pref.getExtras()).thenReturn(new Bundle());
    assertTrue(activity.onPreferenceStartFragment(null, pref));
    Intent started = shadowOf(activity).getNextStartedActivity();
    assertEquals(SettingsActivity.class.getName(), started.getComponent().getClassName());
    assertEquals("some.Sub.Fragment", started.getStringExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT));
    assertEquals("Sub", started.getCharSequenceExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_TITLE));
  }
}
