/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.offers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SupportOfferTest {

  private static SupportOffer randomWithThreshold(int threshold) {
    ConfigurationManager cm = mock(ConfigurationManager.class);
    when(cm.getInt(eq(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD), anyInt()))
        .thenReturn(threshold);
    try (MockedStatic<ConfigurationManager> statics =
        Mockito.mockStatic(ConfigurationManager.class)) {
      statics.when(ConfigurationManager::instance).thenReturn(cm);
      return SupportOffer.random();
    }
  }

  @Test
  public void vpnOfferPointsAtTheVpnPage() {
    SupportOffer offer = SupportOffer.forType(SupportOffer.Type.VPN);
    assertEquals(SupportOffer.Type.VPN, offer.type);
    assertEquals(Constants.FROSTWIRE_VPN_URL, offer.getUrl());
    assertEquals(R.string.support_offer_vpn_title, offer.titleRes);
    assertEquals(R.string.support_offer_vpn_message, offer.messageRes);
    assertEquals(R.string.support_offer_vpn_action, offer.actionTextRes);
    assertEquals(R.string.support_offer_vpn_badge, offer.badgeTextRes);
    assertNotEquals(0, offer.iconRes);
  }

  @Test
  public void donationOfferPointsAtTheGivePageTaggedWithTheSource() {
    SupportOffer offer = SupportOffer.forType(SupportOffer.Type.DONATION);
    assertEquals(SupportOffer.Type.DONATION, offer.type);
    assertEquals(Constants.FROSTWIRE_GIVE_URL + "android", offer.getUrl());
    assertEquals(R.string.support_offer_donation_title, offer.titleRes);
    assertEquals(R.string.support_offer_donation_message, offer.messageRes);
    assertEquals(R.string.support_offer_donation_action, offer.actionTextRes);
    assertEquals(R.string.support_offer_donation_badge, offer.badgeTextRes);
    assertNotEquals(0, offer.iconRes);
  }

  @Test
  public void aThresholdOfZeroAlwaysPicksDonationsAndOneHundredAlwaysVpn() {
    for (int i = 0; i < 30; i++) {
      assertEquals(SupportOffer.Type.DONATION, randomWithThreshold(0).type);
      assertEquals(SupportOffer.Type.VPN, randomWithThreshold(100).type);
    }
  }

  @Test
  public void outOfRangeThresholdsAreClamped() {
    for (int i = 0; i < 30; i++) {
      assertEquals(SupportOffer.Type.DONATION, randomWithThreshold(-50).type);
      assertEquals(SupportOffer.Type.VPN, randomWithThreshold(1000).type);
    }
  }

  @Test
  public void aMidThresholdPicksBothKindsEventually() {
    boolean vpn = false;
    boolean donation = false;
    for (int i = 0; i < 400 && !(vpn && donation); i++) {
      if (randomWithThreshold(50).type == SupportOffer.Type.VPN) {
        vpn = true;
      } else {
        donation = true;
      }
    }
    assertTrue("both offers should show at 50%", vpn && donation);
  }

  @Test
  public void openingAnOfferLaunchesItsUrlInABrowser() throws InterruptedException {
    Application app = ApplicationProvider.getApplicationContext();
    SupportOffer.forType(SupportOffer.Type.VPN).open(app);
    Intent started = OfferTestSupport.nextStartedActivity(app);
    assertNotNull(started);
    assertEquals(Intent.ACTION_VIEW, started.getAction());
    assertEquals(Constants.FROSTWIRE_VPN_URL, started.getDataString());
  }
}
