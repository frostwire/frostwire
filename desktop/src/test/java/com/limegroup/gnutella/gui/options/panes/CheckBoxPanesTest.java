/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.options.panes.PaneSupport.Body;
import com.limegroup.gnutella.settings.QuestionsHandler;
import com.limegroup.gnutella.settings.SearchSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.settings.StartupSettings;
import com.limegroup.gnutella.settings.StatusBarSettings;
import com.limegroup.gnutella.settings.UpdateManagerSettings;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.BooleanSetting;

/**
 * The Options panes that are one check box over one boolean setting: opening the pane shows the
 * stored value, flipping the box makes the pane dirty, and applying writes the box back.
 */
class CheckBoxPanesTest {
  private boolean startup;
  private boolean firewall;
  private boolean quality;
  private boolean bandwidth;
  private boolean partial;
  private boolean details;
  private boolean promos;
  private boolean recommendations;
  private int licenseWarning;

  @BeforeEach
  void save() {
    startup = StartupSettings.RUN_ON_STARTUP.getValue();
    firewall = StatusBarSettings.FIREWALL_DISPLAY_ENABLED.getValue();
    quality = StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.getValue();
    bandwidth = StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.getValue();
    partial = SharingSettings.ALLOW_PARTIAL_SHARING.getValue();
    details = SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.getValue();
    promos = UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS.getValue();
    recommendations = UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.getValue();
    licenseWarning = QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue();
  }

  @AfterEach
  void restore() {
    StartupSettings.RUN_ON_STARTUP.setValue(startup);
    StatusBarSettings.FIREWALL_DISPLAY_ENABLED.setValue(firewall);
    StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED.setValue(quality);
    StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED.setValue(bandwidth);
    SharingSettings.ALLOW_PARTIAL_SHARING.setValue(partial);
    SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(details);
    UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS.setValue(promos);
    UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.setValue(recommendations);
    QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.setValue(licenseWarning);
  }

  /** Opening shows the setting, flipping dirties the pane, and flipping back cleans it. */
  private static <P extends AbstractPaneItem> void assertFollowsSetting(
      Body<P> factory, BooleanSetting setting) throws Exception {
    for (boolean stored : new boolean[] {true, false}) {
      setting.setValue(stored);
      P pane = PaneSupport.onEdt(factory);
      PaneSupport.onEdt(pane::initOptions);
      JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
      assertEquals(stored, box.isSelected(), "the pane shows the stored value");
      assertFalse(pane.isDirty(), "just opened, nothing to save");
      box.setSelected(!stored);
      assertTrue(pane.isDirty(), "flipping the box leaves something to save");
      box.setSelected(stored);
      assertFalse(pane.isDirty(), "flipping it back leaves nothing to save");
    }
  }

  /** Applying writes what the box shows, in both directions. */
  private static <P extends AbstractPaneItem> void assertApplyWrites(
      Body<P> factory, BooleanSetting setting) throws Exception {
    for (boolean wanted : new boolean[] {true, false}) {
      setting.setValue(!wanted);
      P pane = PaneSupport.onEdt(factory);
      PaneSupport.onEdt(pane::initOptions);
      PaneSupport.only(pane.getContainer(), JCheckBox.class).setSelected(wanted);
      assertFalse(pane.applyOptions(), "no restart needed");
      assertEquals(wanted, setting.getValue());
      assertFalse(pane.isDirty(), "applied, so clean");
    }
  }

  @Test
  void startupFollowsItsSetting() throws Exception {
    // Applying it also adds or removes the OS login item, which a test must not do.
    assertFollowsSetting(StartupPaneItem::new, StartupSettings.RUN_ON_STARTUP);
  }

  @Test
  void statusBarIndicatorsFollowTheirSettings() throws Exception {
    // Applying refreshes the real status line, which needs the whole main window.
    assertFollowsSetting(
        StatusBarFirewallPaneItem::new, StatusBarSettings.FIREWALL_DISPLAY_ENABLED);
    assertFollowsSetting(
        StatusBarConnectionQualityPaneItem::new,
        StatusBarSettings.CONNECTION_QUALITY_DISPLAY_ENABLED);
    assertFollowsSetting(
        StatusBarBandwidthPaneItem::new, StatusBarSettings.BANDWIDTH_DISPLAY_ENABLED);
  }

  @Test
  void partialSharingReadsAndWrites() throws Exception {
    assertFollowsSetting(PartialFileSharingPaneItem::new, SharingSettings.ALLOW_PARTIAL_SHARING);
    assertApplyWrites(PartialFileSharingPaneItem::new, SharingSettings.ALLOW_PARTIAL_SHARING);
  }

  @Test
  void detailsPageReadsAndWrites() throws Exception {
    assertFollowsSetting(
        DetailsPaneItem::new, SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START);
    assertApplyWrites(DetailsPaneItem::new, SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START);
  }

  @Test
  void frostClickPromotionsReadAndWrite() throws Exception {
    assertFollowsSetting(
        ShowPromoOverlaysPaneItem::new, UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS);
    assertApplyWrites(
        ShowPromoOverlaysPaneItem::new, UpdateManagerSettings.SHOW_PROMOTION_OVERLAYS);
  }

  @Test
  void recommendationsReadAndWrite() throws Exception {
    assertFollowsSetting(
        ShowFrostWireRecommendationsPaneItem::new,
        UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS);
    assertApplyWrites(
        ShowFrostWireRecommendationsPaneItem::new,
        UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS);
  }

  /** The license warning box is the inverse of the "skip the first download warning" answer. */
  @Test
  void licenseWarningIsTheInverseOfSkippingIt() throws Exception {
    QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.setValue(0);
    DownloadLicenseWarningPaneItem pane = PaneSupport.onEdt(DownloadLicenseWarningPaneItem::new);
    pane.initOptions();
    JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    assertTrue(box.isSelected(), "warn by default");
    assertFalse(pane.isDirty());

    box.setSelected(false);
    assertTrue(pane.isDirty());
    pane.applyOptions();
    assertEquals(DialogOption.YES.toInt(), QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue());

    pane.initOptions();
    assertFalse(box.isSelected(), "skipping is shown as an unchecked box");
    assertFalse(pane.isDirty());
    box.setSelected(true);
    assertTrue(pane.isDirty());
    pane.applyOptions();
    assertEquals(0, QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue());
  }
}
