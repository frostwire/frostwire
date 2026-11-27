/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.offers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import com.frostwire.android.gui.activities.MainActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * Offers is now an in-house stub: no ad network, so showing an interstitial only runs the "what to
 * do afterward" flow. It has its own SDK level because forceDisabledAds is a one-way static.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
public class OffersTest {

  @Test
  public void thereAreNoRemoveAdsOffersAndDebugModeIsOff() {
    assertFalse(Offers.removeAdsOffersEnabled());
    assertFalse(Offers.DEBUG_MODE);
    assertEquals("interstitial_main", Offers.PLACEMENT_INTERSTITIAL_MAIN);
  }

  @Test
  public void dismissAfterwardFinishesTheActivity() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Offers.showInterstitial(activity, Offers.PLACEMENT_INTERSTITIAL_MAIN, false, true);
    assertTrue(activity.isFinishing());
  }

  @Test
  public void withNoFollowUpTheActivityIsLeftAlone() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Offers.showInterstitial(activity, Offers.PLACEMENT_INTERSTITIAL_MAIN, false, false);
    assertFalse(activity.isFinishing());
    assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
  }

  @Test
  public void shutdownAfterwardFromAPlainActivitySendsTheShutdownIntent() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    Offers.showInterstitial(activity, Offers.PLACEMENT_INTERSTITIAL_MAIN, true, false);
    Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
    assertNotNull(started);
    assertTrue(started.getBooleanExtra("shutdown-frostwire", false));
    assertEquals(MainActivity.class.getName(), started.getComponent().getClassName());
    assertFalse(activity.isFinishing());
  }

  @Test
  public void shutdownAfterwardFromTheMainActivityShutsItDownDirectly() {
    MainActivity main = mock(MainActivity.class);
    Offers.showInterstitialOfferIfNecessary(
        main, Offers.PLACEMENT_INTERSTITIAL_MAIN, true, true, true);
    verify(main).finish();
    verify(main).shutdown();
  }

  @Test
  public void withoutShutdownTheMainActivityIsNotShutDown() {
    MainActivity main = mock(MainActivity.class);
    Offers.showInterstitial(main, Offers.PLACEMENT_INTERSTITIAL_MAIN, false, false);
    verify(main, never()).shutdown();
    verify(main, never()).finish();
  }

  @Test
  public void aNullActivityIsToleratedAndNetworkHooksAreNoOps() {
    Offers.showInterstitial(null, Offers.PLACEMENT_INTERSTITIAL_MAIN, true, true);
    Offers.initAdNetworks(null);
    Offers.stopAdNetworks(null);
  }

  @Test
  public void forceDisabledAdsSticksForTheProcess() {
    Application app =
        (Application) androidx.test.core.app.ApplicationProvider.getApplicationContext();
    Offers.forceDisabledAds(app);
    assertTrue(Offers.disabledAds());
  }
}
