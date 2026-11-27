/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.ListEditor;
import com.limegroup.gnutella.gui.WholeNumberField;
import com.limegroup.gnutella.settings.FilterSettings;
import com.limegroup.gnutella.settings.SearchSettings;
import java.util.Vector;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The search related Options panes: how many searches run at once, filters and Smart Search. */
class SearchPanesTest {
  private int parallel;
  private String[] banned;
  private boolean adult;
  private boolean smart;

  @BeforeEach
  void save() {
    parallel = SearchSettings.PARALLEL_SEARCH.getValue();
    banned = FilterSettings.BANNED_WORDS.getValue();
    adult = FilterSettings.FILTER_ADULT.getValue();
    smart = SearchSettings.SMART_SEARCH_ENABLED.getValue();
  }

  @AfterEach
  void restore() {
    SearchSettings.PARALLEL_SEARCH.setValue(parallel);
    FilterSettings.BANNED_WORDS.setValue(banned);
    FilterSettings.FILTER_ADULT.setValue(adult);
    SearchSettings.SMART_SEARCH_ENABLED.setValue(smart);
  }

  @Test
  void maximumSearchesShowsAndSavesTheNumber() throws Exception {
    SearchSettings.PARALLEL_SEARCH.setValue(4);
    MaximumSearchesPaneItem pane = PaneSupport.onEdt(MaximumSearchesPaneItem::new);
    pane.initOptions();
    WholeNumberField field = PaneSupport.only(pane.getContainer(), WholeNumberField.class);
    assertEquals(4, field.getValue());
    assertFalse(pane.isDirty());

    field.setValue(7);
    assertTrue(pane.isDirty());
    assertFalse(pane.applyOptions());
    assertEquals(7, SearchSettings.PARALLEL_SEARCH.getValue());
    assertFalse(pane.isDirty());
  }

  @Test
  void maximumSearchesAtTheLimitIsAccepted() throws Exception {
    MaximumSearchesPaneItem pane = PaneSupport.onEdt(MaximumSearchesPaneItem::new);
    pane.initOptions();
    PaneSupport.only(pane.getContainer(), WholeNumberField.class)
        .setValue(SearchSettings.MAXIMUM_PARALLEL_SEARCH);
    pane.applyOptions();
    assertEquals(SearchSettings.MAXIMUM_PARALLEL_SEARCH, SearchSettings.PARALLEL_SEARCH.getValue());
  }

  @Test
  void filterResultsShowsTheBannedWordsAndTheAdultBox() throws Exception {
    FilterSettings.BANNED_WORDS.setValue(new String[] {"alpha", "beta"});
    FilterSettings.FILTER_ADULT.setValue(true);
    IgnoreResultsPaneItem pane = PaneSupport.onEdt(IgnoreResultsPaneItem::new);
    pane.initOptions();
    assertEquals(
        new Vector<>(java.util.List.of("alpha", "beta")),
        PaneSupport.only(pane.getContainer(), ListEditor.class).getModel());
    assertTrue(PaneSupport.only(pane.getContainer(), JCheckBox.class).isSelected());
    assertFalse(pane.isDirty());
  }

  @Test
  void filterResultsSavesEditsToTheWordListAndTheAdultBox() throws Exception {
    FilterSettings.BANNED_WORDS.setValue(new String[] {"alpha"});
    FilterSettings.FILTER_ADULT.setValue(false);
    IgnoreResultsPaneItem pane = PaneSupport.onEdt(IgnoreResultsPaneItem::new);
    pane.initOptions();

    PaneSupport.only(pane.getContainer(), ListEditor.class).getModel().add("gamma");
    assertTrue(pane.isDirty(), "a new word is a change");
    PaneSupport.only(pane.getContainer(), JCheckBox.class).setSelected(true);

    assertFalse(pane.applyOptions());
    assertArrayEquals(new String[] {"alpha", "gamma"}, FilterSettings.BANNED_WORDS.getValue());
    assertTrue(FilterSettings.FILTER_ADULT.getValue());
    assertFalse(pane.isDirty());
  }

  @Test
  void filterResultsAdultBoxAloneMakesThePaneDirty() throws Exception {
    IgnoreResultsPaneItem pane = PaneSupport.onEdt(IgnoreResultsPaneItem::new);
    pane.initOptions();
    JCheckBox adultBox = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    adultBox.setSelected(!adultBox.isSelected());
    assertTrue(pane.isDirty());
  }

  @Test
  void smartSearchSavesItsBox() throws Exception {
    SearchSettings.SMART_SEARCH_ENABLED.setValue(true);
    SmartSearchDBPaneItem pane = PaneSupport.onEdt(SmartSearchDBPaneItem::new);
    JCheckBox box = PaneSupport.only(pane.getContainer(), JCheckBox.class);
    assertTrue(box.isSelected(), "built with the stored value");
    assertFalse(pane.isDirty());

    box.setSelected(false);
    assertTrue(pane.isDirty());
    assertFalse(pane.applyOptions());
    assertFalse(SearchSettings.SMART_SEARCH_ENABLED.getValue());
    assertFalse(pane.isDirty());
  }
}
