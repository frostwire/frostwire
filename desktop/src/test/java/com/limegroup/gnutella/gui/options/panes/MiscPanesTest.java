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

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.DesktopSearchEngineAdapter;
import com.limegroup.gnutella.gui.search.DesktopSearchEngines;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.QuestionsHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.setting.BooleanSetting;

/** The Performance, Popups and Search Engines Options panes, built for real, headless. */
class MiscPanesTest {
  private String performance;
  private int licenseWarning;
  private final Map<BooleanSetting, Boolean> engineSettings = new LinkedHashMap<>();

  @BeforeEach
  void save() {
    performance = ApplicationSettings.PERFORMANCE_MODE.getValue();
    licenseWarning = QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue();
    for (SearchEngine engine : DesktopSearchEngines.all()) {
      BooleanSetting setting = DesktopSearchEngineAdapter.settingFor(engine.getId());
      engineSettings.put(setting, setting.getValue());
    }
  }

  @AfterEach
  void restore() {
    ApplicationSettings.PERFORMANCE_MODE.setValue(performance);
    PerformanceMode.resolve(Choice.FULL, null);
    QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.setValue(licenseWarning);
    engineSettings.forEach(BooleanSetting::setValue);
  }

  private static JRadioButton radio(PerformancePaneItem pane, String text) {
    return PaneSupport.all(pane.getContainer(), JRadioButton.class).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow();
  }

  private static String runningNow(PerformancePaneItem pane) {
    return PaneSupport.all(pane.getContainer(), javax.swing.JLabel.class).stream()
        .map(javax.swing.JLabel::getText)
        .filter(
            t -> I18n.tr("Running now: Full").equals(t) || I18n.tr("Running now: Lite").equals(t))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void thePerformancePaneOpensOnTheSavedChoice() throws Exception {
    for (Choice saved : Choice.values()) {
      ApplicationSettings.PERFORMANCE_MODE.setValue(saved.name());
      PerformancePaneItem pane = PaneSupport.onEdt(PerformancePaneItem::new);
      pane.initOptions();
      assertEquals(saved, pane.selectedChoice());
      assertFalse(pane.isDirty());
      assertEquals(
          saved == Choice.AUTOMATIC, radio(pane, I18n.tr("Automatic (recommended)")).isSelected());
      assertEquals(saved == Choice.FULL, radio(pane, I18n.tr("Full")).isSelected());
      assertEquals(saved == Choice.LITE, radio(pane, I18n.tr("Lite")).isSelected());
    }
  }

  @Test
  void choosingAnotherModeMakesThePaneDirtyAndApplyingSavesIt() throws Exception {
    ApplicationSettings.PERFORMANCE_MODE.setValue(Choice.FULL.name());
    PerformanceMode.resolve(Choice.FULL, null);
    PerformancePaneItem pane = PaneSupport.onEdt(PerformancePaneItem::new);
    pane.initOptions();
    assertEquals(I18n.tr("Running now: Full"), runningNow(pane));

    radio(pane, I18n.tr("Lite")).setSelected(true);
    assertTrue(pane.isDirty());
    assertFalse(pane.applyOptions(), "Lite applies right away, no restart");
    assertEquals(Choice.LITE.name(), ApplicationSettings.PERFORMANCE_MODE.getValue());
    assertTrue(PerformanceMode.isLite());
    assertFalse(pane.isDirty());
    assertEquals(I18n.tr("Running now: Lite"), runningNow(pane));
  }

  @Test
  void applyingTheSameModeChangesNothing() throws Exception {
    ApplicationSettings.PERFORMANCE_MODE.setValue(Choice.AUTOMATIC.name());
    PerformancePaneItem pane = PaneSupport.onEdt(PerformancePaneItem::new);
    pane.initOptions();
    assertFalse(pane.applyOptions());
    assertEquals(Choice.AUTOMATIC.name(), ApplicationSettings.PERFORMANCE_MODE.getValue());
  }

  @Test
  void thePopupsBoxResetsEveryAnsweredQuestionWhenApplied() throws Exception {
    QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.setValue(DialogOption.YES.toInt());
    PopupsPaneItem pane = PaneSupport.onEdt(PopupsPaneItem::new);
    pane.initOptions();
    JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    assertFalse(box.isSelected(), "always shown unchecked");
    assertFalse(pane.isDirty());

    pane.applyOptions();
    assertEquals(
        DialogOption.YES.toInt(),
        QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue(),
        "unchecked: nothing is reset");

    box.setSelected(true);
    assertTrue(pane.isDirty());
    assertFalse(pane.applyOptions());
    assertEquals(
        QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getDefaultValue(),
        QuestionsHandler.SKIP_FIRST_DOWNLOAD_WARNING.getValue());
    assertFalse(box.isSelected(), "the box clears itself");
    assertFalse(pane.isDirty());
  }

  private static List<JCheckBox> engineBoxes(SearchEnginesPaneItem pane) {
    return PaneSupport.all(pane.getContainer(), JCheckBox.class);
  }

  private static JCheckBox toggleAll(SearchEnginesPaneItem pane) {
    return engineBoxes(pane).stream()
        .filter(b -> b.getText().equals(I18n.tr("Check/Uncheck all")))
        .findFirst()
        .orElseThrow();
  }

  private static List<JCheckBox> engineOnly(SearchEnginesPaneItem pane) {
    JCheckBox all = toggleAll(pane);
    return engineBoxes(pane).stream().filter(b -> b != all).toList();
  }

  @Test
  void everyEngineHasABoxAndTheToggleFollowsThem() throws Exception {
    engineSettings.keySet().forEach(s -> s.setValue(true));
    SearchEnginesPaneItem pane = PaneSupport.onEdt(SearchEnginesPaneItem::new);
    assertEquals(engineSettings.size(), engineOnly(pane).size());
    assertTrue(toggleAll(pane).isSelected(), "all enabled");

    JCheckBox first = engineOnly(pane).get(0);
    JCheckBox second = engineOnly(pane).get(1);
    BooleanSetting secondSetting = engineSettings.keySet().stream().toList().get(1);
    second.setSelected(false);
    assertFalse(toggleAll(pane).isSelected(), "one is off");
    assertFalse(
        engineSettings.keySet().stream().filter(s -> !s.getValue()).findAny().isEmpty(),
        "the engine was switched off right away");
    assertTrue(first.isSelected());
    second.setSelected(true);
    assertTrue(toggleAll(pane).isSelected(), "all back on");
    assertTrue(secondSetting.getValue());
  }

  /** The Distributed Search engine is the first box on the page, whatever the registry order. */
  private static BooleanSetting distributedSetting() {
    return DesktopSearchEngineAdapter.settingFor(SearchEngineID.DISTRIBUTED_ID);
  }

  @Test
  void uncheckingAllLeavesDistributedSearchOnAndCheckingAllTurnsEveryEngineOn() throws Exception {
    engineSettings.keySet().forEach(s -> s.setValue(true));
    SearchEnginesPaneItem pane = PaneSupport.onEdt(SearchEnginesPaneItem::new);

    toggleAll(pane).setSelected(false);
    List<JCheckBox> boxes = engineOnly(pane);
    assertTrue(boxes.get(0).isSelected(), "one engine always stays on");
    for (JCheckBox box : boxes.subList(1, boxes.size())) {
      assertFalse(box.isSelected());
    }
    assertTrue(distributedSetting().getValue());
    for (BooleanSetting setting : engineSettings.keySet()) {
      if (setting != distributedSetting()) {
        assertFalse(setting.getValue());
      }
    }

    toggleAll(pane).setSelected(true);
    for (BooleanSetting setting : engineSettings.keySet()) {
      assertTrue(setting.getValue());
    }
    for (JCheckBox box : engineOnly(pane)) {
      assertTrue(box.isSelected());
    }
  }

  @Test
  void theLastEngineCannotBeSwitchedOff() throws Exception {
    engineSettings.keySet().forEach(s -> s.setValue(true));
    SearchEnginesPaneItem pane = PaneSupport.onEdt(SearchEnginesPaneItem::new);
    toggleAll(pane).setSelected(false);
    JCheckBox last = engineOnly(pane).get(0);
    assertTrue(last.isSelected());
    last.setSelected(false);
    assertTrue(last.isSelected(), "unchecking the only engine turns it back on");
    assertTrue(distributedSetting().getValue());
  }

  @Test
  void theSearchEnginesPaneHasNothingToApply() throws Exception {
    SearchEnginesPaneItem pane = PaneSupport.onEdt(SearchEnginesPaneItem::new);
    pane.initOptions();
    assertFalse(pane.isDirty());
    assertFalse(pane.applyOptions());
  }
}
