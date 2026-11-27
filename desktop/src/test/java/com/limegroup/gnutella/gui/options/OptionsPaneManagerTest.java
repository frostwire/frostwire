/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.options.panes.AbstractPaneItem;
import com.limegroup.gnutella.gui.options.panes.DetailsPaneItem;
import com.limegroup.gnutella.gui.options.panes.PartialFileSharingPaneItem;
import com.limegroup.gnutella.settings.SearchSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Options window's pane stack: one card per tree node, created the first time its node is
 * shown, and OK/Apply running over every pane that was ever opened.
 */
class OptionsPaneManagerTest {
  private boolean details;
  private boolean partial;

  @BeforeEach
  void save() {
    details = SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.getValue();
    partial = SharingSettings.ALLOW_PARTIAL_SHARING.getValue();
  }

  @AfterEach
  void restore() {
    SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(details);
    SharingSettings.ALLOW_PARTIAL_SHARING.setValue(partial);
  }

  private static OptionsTreeNode node(String key, List<Class<? extends AbstractPaneItem>> classes) {
    OptionsTreeNode node = new OptionsTreeNode(key, key);
    node.setClasses(classes);
    return node;
  }

  private static List<JCheckBox> boxes(Component root) {
    List<JCheckBox> found = new ArrayList<>();
    if (root instanceof JCheckBox box) {
      found.add(box);
    }
    if (root instanceof Container c) {
      for (Component child : c.getComponents()) {
        found.addAll(boxes(child));
      }
    }
    return found;
  }

  private static OptionsPaneManager manager() throws Exception {
    OptionsPaneManager[] manager = new OptionsPaneManager[1];
    SwingUtilities.invokeAndWait(() -> manager[0] = new OptionsPaneManager());
    return manager[0];
  }

  @Test
  void aNodeWithoutPanesCannotBeShown() throws Exception {
    OptionsPaneManager manager = manager();
    OptionsTreeNode empty = new OptionsTreeNode("EMPTY", "Empty");
    SwingUtilities.invokeAndWait(
        () -> assertThrows(IllegalArgumentException.class, () -> manager.show(empty)));
  }

  @Test
  void showingANodeBuildsItsPanesOnceAndOpensThemOnTheStoredSettings() throws Exception {
    SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(true);
    SharingSettings.ALLOW_PARTIAL_SHARING.setValue(false);
    OptionsPaneManager manager = manager();
    OptionsTreeNode node =
        node("ONE", List.of(DetailsPaneItem.class, PartialFileSharingPaneItem.class));
    SwingUtilities.invokeAndWait(
        () -> {
          manager.show(node);
          JPanel stack = (JPanel) manager.getComponent();
          assertEquals(1, stack.getComponentCount(), "one card");
          List<JCheckBox> boxes = boxes(stack);
          assertEquals(2, boxes.size(), "one check box per pane in the card");
          assertTrue(boxes.get(0).isSelected(), "details shows the stored yes");
          assertFalse(boxes.get(1).isSelected(), "partial sharing shows the stored no");

          manager.show(node);
          assertEquals(1, stack.getComponentCount(), "showing it again builds nothing new");
        });
  }

  @Test
  void applyingWritesEveryOpenedPaneAndDirtyTracksThem() throws Exception {
    SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(false);
    SharingSettings.ALLOW_PARTIAL_SHARING.setValue(false);
    OptionsPaneManager manager = manager();
    SwingUtilities.invokeAndWait(
        () -> {
          manager.show(node("A", List.of(DetailsPaneItem.class)));
          manager.show(node("B", List.of(PartialFileSharingPaneItem.class)));
          assertFalse(manager.isDirty());

          List<JCheckBox> boxes = boxes(manager.getComponent());
          assertEquals(2, boxes.size());
          boxes.get(0).setSelected(true);
          assertTrue(manager.isDirty(), "a change in the first card");
          boxes.get(1).setSelected(true);
          try {
            manager.applyOptions();
          } catch (java.io.IOException e) {
            throw new AssertionError(e);
          }
          assertTrue(SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.getValue());
          assertTrue(SharingSettings.ALLOW_PARTIAL_SHARING.getValue());
          assertFalse(manager.isDirty());
        });
  }

  @Test
  void reopeningRereadsTheSettingsIntoEveryPane() throws Exception {
    SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(false);
    OptionsPaneManager manager = manager();
    SwingUtilities.invokeAndWait(
        () -> {
          manager.show(node("A", List.of(DetailsPaneItem.class)));
          JCheckBox box = boxes(manager.getComponent()).get(0);
          box.setSelected(true);
          assertTrue(manager.isDirty());
          manager.initOptions();
          assertFalse(box.isSelected(), "unsaved edits are dropped");
          assertFalse(manager.isDirty());

          SearchSettings.SHOW_DETAIL_PAGE_AFTER_DOWNLOAD_START.setValue(true);
          manager.reinitPane("A");
          assertTrue(box.isSelected());
          manager.reinitPane("NOT_OPENED");
        });
  }

  /**
   * The former SAVE_BASIC_KEY branch built a second pane for the node (frostwire/frostwire#1655).
   */
  @Test
  void showingAnyNodeAddsExactlyOneCard() throws Exception {
    OptionsPaneManager manager = manager();
    SwingUtilities.invokeAndWait(
        () -> {
          manager.show(node("OPTIONS_SAVE_BASIC_MAIN_TITLE", List.of(DetailsPaneItem.class)));
          assertEquals(1, ((JPanel) manager.getComponent()).getComponentCount());
        });
  }
}
