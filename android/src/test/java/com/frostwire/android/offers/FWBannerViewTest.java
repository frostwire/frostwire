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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class FWBannerViewTest {

  private Application app;
  private MockedStatic<ConfigurationManager> statics;
  private FWBannerView banner;
  private Activity activity;

  @Before
  public void setUp() {
    app = ApplicationProvider.getApplicationContext();
    ConfigurationManager cm = mock(ConfigurationManager.class);
    when(cm.getInt(eq(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD), anyInt())).thenReturn(0);
    statics = Mockito.mockStatic(ConfigurationManager.class);
    statics.when(ConfigurationManager::instance).thenReturn(cm);
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    banner = new FWBannerView(activity, null);
    activity.setContentView(banner);
  }

  @After
  public void tearDown() {
    statics.close();
  }

  private <T extends View> T find(int id) {
    T v = banner.findViewById(id);
    assertNotNull(v);
    return v;
  }

  @Test
  public void startsHiddenAndNotLoaded() {
    assertEquals(View.GONE, banner.getVisibility());
    assertFalse(banner.isLoaded());
  }

  @Test
  public void loadingBindsTheDonationOfferShowsItAndTellsTheListener() {
    AtomicInteger loaded = new AtomicInteger();
    banner.setOnBannerLoadedListener(loaded::incrementAndGet);
    banner.loadMaxBanner();
    org.robolectric.shadows.ShadowLooper.idleMainLooper();

    assertEquals(1, loaded.get());
    assertEquals(View.VISIBLE, banner.getVisibility());
    assertTrue(banner.isLoaded());
    TextView title = find(R.id.fwbanner_support_title);
    assertEquals(app.getString(R.string.support_offer_donation_title), title.getText().toString());
    TextView message = find(R.id.fwbanner_support_message);
    assertEquals(
        app.getString(R.string.support_offer_donation_message), message.getText().toString());
  }

  @Test
  public void fallbackLoadingIsTheSameAsLoading() {
    banner.loadFallbackBanner("ignored-unit-id");
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    assertTrue(banner.isLoaded());
  }

  @Test
  public void clickingTheBannerOpensTheOfferUrl() throws InterruptedException {
    banner.loadMaxBanner();
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    banner.performClick();
    Intent started = OfferTestSupport.nextStartedActivity(activity);
    assertNotNull(started);
    assertEquals(Constants.FROSTWIRE_GIVE_URL + "android", started.getDataString());
  }

  @Test
  public void dismissHidesTheBannerAndNotifiesTheListener() {
    AtomicInteger dismissed = new AtomicInteger();
    banner.setOnBannerDismissedListener(dismissed::incrementAndGet);
    banner.loadMaxBanner();
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    ImageButton dismiss = find(R.id.fwbanner_dismiss_button);
    dismiss.performClick();
    assertEquals(1, dismissed.get());
    assertEquals(View.GONE, banner.getVisibility());
    assertFalse(banner.isLoaded());
  }

  @Test
  public void compactModeHidesTheMessageAndShowsTheCompactClose() {
    banner.loadMaxBanner();
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    TextView message = find(R.id.fwbanner_support_message);
    View compactClose = find(R.id.fwbanner_support_compact_close);

    banner.setCompactMode(true);
    assertEquals(View.GONE, message.getVisibility());
    assertEquals(View.VISIBLE, compactClose.getVisibility());

    banner.setCompactMode(false);
    assertEquals(View.VISIBLE, message.getVisibility());
    assertEquals(View.GONE, compactClose.getVisibility());
  }

  @Test
  public void dismissButtonCanBeHiddenAndShown() {
    ImageButton dismiss = find(R.id.fwbanner_dismiss_button);
    banner.setShowDismissButton(false);
    assertEquals(View.GONE, dismiss.getVisibility());
    banner.setShowDismissButton(true);
    assertEquals(View.VISIBLE, dismiss.getVisibility());
  }

  @Test
  public void extendedConstructorAppliesTheDismissButtonChoice() {
    FWBannerView hidden = new FWBannerView(app, null, true, false, false, "unit");
    assertEquals(View.GONE, hidden.findViewById(R.id.fwbanner_dismiss_button).getVisibility());
  }

  @Test
  public void layerVisibilityTogglesTheSupportContainerOrTheWholeView() {
    LinearLayout container = find(R.id.fwbanner_support_container);
    banner.setLayersVisibility(FWBannerView.Layers.APPLOVIN, true);
    assertEquals(View.VISIBLE, container.getVisibility());
    assertEquals(View.VISIBLE, banner.getVisibility());
    banner.setLayersVisibility(FWBannerView.Layers.FALLBACK, false);
    assertEquals(View.GONE, container.getVisibility());
    assertEquals(View.GONE, banner.getVisibility());
    banner.setLayersVisibility(FWBannerView.Layers.ALL, true);
    assertEquals(View.VISIBLE, banner.getVisibility());
  }

  @Test
  public void destroyDropsListenersAndTheLoadedOffer() {
    AtomicInteger loaded = new AtomicInteger();
    banner.setOnBannerLoadedListener(loaded::incrementAndGet);
    banner.loadMaxBanner();
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
    banner.destroy();
    assertFalse(banner.isLoaded());
    assertEquals(View.GONE, banner.getVisibility());
    banner.loadMaxBanner();
    assertEquals("listener was dropped by destroy()", 1, loaded.get());
  }
}
