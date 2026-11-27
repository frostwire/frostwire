/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class WizardPagesTest {

  private Context context;
  private ConfigurationManager config;
  private final List<Boolean> completions = new ArrayList<>();
  private final WizardPageView.OnCompleteListener listener =
      (page, complete) -> completions.add(complete);

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    config = ViewTestSupport.configuration();
    context = ViewTestSupport.themedContext();
  }

  private boolean last() {
    return completions.get(completions.size() - 1);
  }

  // ---- IntentWizardPage

  @Test
  public void intentPageIsCompleteOnlyWhenBothTermsAreAccepted() {
    IntentWizardPage page = new IntentWizardPage(context, null);
    page.onFinishInflate();
    page.setOnCompleteListener(listener);
    assertTrue(page.hasPrevious());
    assertFalse(page.hasNext());
    CheckBox copyright = page.findViewById(R.id.view_intent_wizard_page_check_accept_copyright);
    CheckBox tou = page.findViewById(R.id.view_intent_wizard_page_check_accept_tou);

    page.load();
    assertFalse(last());
    copyright.setChecked(true);
    assertFalse(last());
    tou.setChecked(true);
    assertTrue(last());
    copyright.setChecked(false);
    assertFalse(last());
  }

  @Test
  public void intentPageLinksTheTermsOfUse() {
    IntentWizardPage page = new IntentWizardPage(context, null);
    page.onFinishInflate();
    TextView tos = page.findViewById(R.id.view_intent_wizard_page_text_tos);
    assertEquals(context.getString(R.string.terms_of_use), tos.getText().toString());
    assertTrue(tos.getMovementMethod() instanceof SafeLinkMovementMethod);
  }

  @Test
  public void intentPageWithoutAListenerStillValidates() {
    IntentWizardPage page = new IntentWizardPage(context, null);
    page.onFinishInflate();
    page.load();
    ((CheckBox) page.findViewById(R.id.view_intent_wizard_page_check_accept_tou)).setChecked(true);
  }

  // ---- GeneralWizardPage

  @Test
  public void generalPageLoadsSeedingPreferencesAndSavesThemBack() {
    config.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, true);
    config.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY, false);
    GeneralWizardPage page = new GeneralWizardPage(context, null);
    page.onFinishInflate();
    page.setOnCompleteListener(listener);
    assertFalse(page.hasPrevious());
    assertTrue(page.hasNext());

    page.load();
    CheckBox seed = page.findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents);
    CheckBox wifiOnly =
        page.findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents_wifi_only);
    assertTrue(seed.isChecked());
    assertFalse(wifiOnly.isChecked());
    assertTrue(wifiOnly.isEnabled());
    assertTrue(last());

    wifiOnly.setChecked(true);
    seed.setChecked(false);
    assertFalse(wifiOnly.isEnabled());
    page.finish();
    assertFalse(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS));
    assertTrue(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY));
  }

  @Test
  public void generalPageDisablesWifiOnlyWhenNotSeeding() {
    config.setBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS, false);
    GeneralWizardPage page = new GeneralWizardPage(context, null);
    page.onFinishInflate();
    page.load();
    assertFalse(
        page.findViewById(R.id.view_general_wizard_page_check_seed_finished_torrents_wifi_only)
            .isEnabled());
  }

  @Test
  public void generalPageWelcomesWithTheFlavorAndHidesStorageWithoutSaf() {
    GeneralWizardPage page = new GeneralWizardPage(context, null);
    page.onFinishInflate();
    TextView welcome = page.findViewById(R.id.view_general_wizard_page_welcome_to_frostwire);
    String flavor =
        context.getString(Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? R.string.basic : R.string.plus);
    assertEquals(
        context.getString(R.string.welcome_to_frostwire, flavor), welcome.getText().toString());
    TextView path = page.findViewById(R.id.view_general_wizard_page_storage_path_textview);
    assertEquals(View.GONE, path.getVisibility());
    page.updateStoragePathTextView("/sdcard/FrostWire");
    assertEquals("/sdcard/FrostWire", path.getText().toString());
  }

  // ---- PublicCatalogWizardPage

  @Test
  public void catalogPageOnlyEnablesTheCatalogChoiceOnTheNetwork() {
    config.setBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED, false);
    config.setBoolean(Constants.PREF_KEY_ICEBRIDGE_PUBLIC_CATALOG, false);
    PublicCatalogWizardPage page = new PublicCatalogWizardPage(context, null);
    page.onFinishInflate();
    page.setOnCompleteListener(listener);
    assertTrue(page.hasPrevious());
    assertTrue(page.hasNext());
    page.load();
    CheckBox join = page.findViewById(R.id.view_public_catalog_wizard_page_check_join_network);
    CheckBox catalog = page.findViewById(R.id.view_public_catalog_wizard_page_check_public_catalog);
    assertFalse(join.isChecked());
    assertFalse(catalog.isEnabled());
    assertTrue(last());

    join.setChecked(true);
    assertTrue(catalog.isEnabled());
    catalog.setChecked(true);
    page.finish();
    assertTrue(config.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED));
    assertTrue(config.getBoolean(Constants.PREF_KEY_ICEBRIDGE_PUBLIC_CATALOG));

    join.setChecked(false);
    assertFalse(catalog.isEnabled());
    page.finish();
    assertFalse(config.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED));
  }

  @Test
  public void catalogPageLoadsStoredChoices() {
    config.setBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED, true);
    config.setBoolean(Constants.PREF_KEY_ICEBRIDGE_PUBLIC_CATALOG, true);
    PublicCatalogWizardPage page = new PublicCatalogWizardPage(context, null);
    page.onFinishInflate();
    page.load();
    assertTrue(
        ((CheckBox) page.findViewById(R.id.view_public_catalog_wizard_page_check_join_network))
            .isChecked());
    assertTrue(
        ((CheckBox) page.findViewById(R.id.view_public_catalog_wizard_page_check_public_catalog))
            .isChecked());
  }
}
