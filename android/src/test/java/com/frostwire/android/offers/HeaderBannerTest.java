/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.offers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class HeaderBannerTest {

  private Application app;
  private MockedStatic<ConfigurationManager> statics;
  private HeaderBanner banner;

  @Before
  public void setUp() {
    app = ApplicationProvider.getApplicationContext();
    ConfigurationManager cm = mock(ConfigurationManager.class);
    // Always roll the VPN offer so the bound text is deterministic.
    when(cm.getInt(eq(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD), anyInt())).thenReturn(100);
    statics = Mockito.mockStatic(ConfigurationManager.class);
    statics.when(ConfigurationManager::instance).thenReturn(cm);
    banner = new HeaderBanner(app, null);
    banner.onFinishInflate(); // the platform calls this when the banner is inflated from XML
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
  public void updateComponentsBindsAnOfferAndShowsTheBanner() {
    banner.updateComponents();
    assertEquals(View.VISIBLE, banner.getVisibility());
    TextView title = find(R.id.view_search_header_banner_title);
    assertEquals(app.getString(R.string.support_offer_vpn_title), title.getText().toString());
    TextView message = find(R.id.view_search_header_banner_message);
    assertEquals(app.getString(R.string.support_offer_vpn_message), message.getText().toString());
    Button action = find(R.id.view_search_header_banner_action);
    assertEquals(app.getString(R.string.support_offer_vpn_action), action.getText().toString());
    ImageButton dismiss = find(R.id.view_search_header_banner_dismiss_banner_button);
    assertEquals(View.VISIBLE, dismiss.getVisibility());
  }

  @Test
  public void clickingTheActionOpensTheOfferUrl() throws InterruptedException {
    banner.updateComponents();
    Button action = find(R.id.view_search_header_banner_action);
    action.performClick();
    Intent started = OfferTestSupport.nextStartedActivity(app);
    assertNotNull(started);
    assertEquals(Constants.FROSTWIRE_VPN_URL, started.getDataString());
  }

  @Test
  public void dismissingHidesTheBannerAndDebouncesTheNextUpdate() {
    banner.updateComponents();
    ImageButton dismiss = find(R.id.view_search_header_banner_dismiss_banner_button);
    dismiss.performClick();
    assertEquals(View.GONE, banner.getVisibility());

    banner.updateComponents();
    assertEquals("a dismissed banner stays hidden for a minute", View.GONE, banner.getVisibility());
  }

  @Test
  public void anotherBannerTypeOnlyTogglesTheSupportContainer() {
    LinearLayout container = find(R.id.view_search_header_banner_support_container);
    banner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.FALLBACK, true);
    assertEquals(View.VISIBLE, container.getVisibility());
    assertEquals(View.VISIBLE, banner.getVisibility());

    banner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.FALLBACK, false);
    assertEquals(View.GONE, container.getVisibility());
    assertEquals(View.GONE, banner.getVisibility());
  }

  @Test
  public void allTogglesTheWholeBanner() {
    banner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.ALL, true);
    assertEquals(View.VISIBLE, banner.getVisibility());
    banner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.ALL, false);
    assertEquals(View.GONE, banner.getVisibility());
  }

  @Test
  public void onResumeUpdatesAndNullIsTolerated() {
    HeaderBanner.onResumeHideOrUpdate(null);
    HeaderBanner.onResumeHideOrUpdate(banner);
    assertEquals(View.VISIBLE, banner.getVisibility());
  }

  @Test
  public void destroyHidesTheBannerAndLaterUpdatesDoNotCrash() {
    banner.updateComponents();
    HeaderBanner.destroy(null);
    HeaderBanner.destroy(banner);
    assertEquals(View.GONE, banner.getVisibility());
    // Views are released, so binding is skipped instead of throwing.
    banner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.APPLOVIN, true);
    assertEquals(View.VISIBLE, banner.getVisibility());
    banner.updateComponents();
  }
}
