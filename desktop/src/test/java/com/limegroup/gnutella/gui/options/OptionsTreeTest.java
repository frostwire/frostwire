/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

/** The Options window's tree: how nodes nest, how they are found and how the search box filters. */
class OptionsTreeTest {
  private static final String ROOT = OptionsMediator.ROOT_NODE_KEY;

  @Test
  void nodesAreAddedUnderTheRootAndUnderTheirGroup() {
    OptionsTreeModel model = new OptionsTreeModel();
    OptionsTreeNode group = model.addNode(ROOT, "GROUP", "Group");
    OptionsTreeNode child = model.addNode("GROUP", "CHILD", "Child");
    OptionsTreeNode sibling = model.addNode("GROUP", "SIBLING", "Sibling");
    assertNotNull(group);
    assertNotNull(child);
    assertSame(model.getRoot(), group.getParent());
    assertSame(group, child.getParent());
    assertSame(group, sibling.getParent());
    assertEquals(2, group.getChildCount());
    assertEquals("Child", child.toString());
    assertEquals("CHILD", child.getTitleKey());
  }

  @Test
  void aNodeWithoutAKnownParentIsNotAdded() {
    OptionsTreeModel model = new OptionsTreeModel();
    model.addNode(ROOT, "GROUP", "Group");
    assertNull(model.addNode("NO_SUCH_GROUP", "CHILD", "Child"));
    assertEquals(1, ((OptionsTreeNode) model.getRoot()).getChildCount());
  }

  @Test
  void aNodeCarriesTheClassesOfItsPanes() {
    OptionsTreeNode node = new OptionsTreeNode("KEY", "Name");
    assertNull(node.getClasses());
    node.setClasses(java.util.List.of());
    assertEquals(0, node.getClasses().size());
  }

  private static JTree treeOf(OptionsTreeManager manager) {
    Component scroll = manager.getComponent();
    return (JTree) ((JScrollPane) scroll).getViewport().getView();
  }

  private static OptionsTreeManager manager() throws Exception {
    OptionsTreeManager[] manager = new OptionsTreeManager[1];
    SwingUtilities.invokeAndWait(
        () -> {
          manager[0] = new OptionsTreeManager();
          manager[0].addNode(ROOT, "NET", "Network", null);
          manager[0].addNode("NET", "PROXY", "Proxy", "Proxy server socks http");
          manager[0].addNode("NET", "I2P", "I2P", "Anonymous tunnels router");
          manager[0].addNode(ROOT, "LIB", "Library", "Folders included library");
        });
    return manager[0];
  }

  @Test
  void selectingAKeyExpandsAndSelectsItsNode() throws Exception {
    OptionsTreeManager manager = manager();
    JTree tree = treeOf(manager);
    SwingUtilities.invokeAndWait(
        () -> {
          manager.setSelection("PROXY");
          TreePath selected = tree.getSelectionPath();
          assertNotNull(selected);
          assertEquals("PROXY", ((OptionsTreeNode) selected.getLastPathComponent()).getTitleKey());
          assertTrue(tree.isExpanded(selected.getParentPath()), "its group opened");
        });
  }

  @Test
  void selectingNullOrAnUnknownKeyChangesNothing() throws Exception {
    OptionsTreeManager manager = manager();
    JTree tree = treeOf(manager);
    SwingUtilities.invokeAndWait(
        () -> {
          manager.setSelection(null);
          manager.setSelection("NOPE");
          assertNull(tree.getSelectionPath());
        });
  }

  @Test
  void theSearchBoxKeepsOnlyTheMatchingNodesAndTheirGroups() throws Exception {
    OptionsTreeManager manager = manager();
    JTree tree = treeOf(manager);
    SwingUtilities.invokeAndWait(
        () -> {
          TreeModel model = tree.getModel();
          Object root = model.getRoot();
          assertEquals(2, model.getChildCount(root));

          manager.setFilterText("proxy");
          assertEquals(1, model.getChildCount(root), "only the Network group is left");
          Object network = model.getChild(root, 0);
          assertEquals("Network", network.toString());
          assertEquals(1, model.getChildCount(network));
          assertEquals("Proxy", model.getChild(network, 0).toString());

          manager.setFilterText("prox serv");
          assertEquals(1, model.getChildCount(root), "every word has to match a keyword");

          manager.setFilterText("proxy tunnels");
          assertEquals(0, model.getChildCount(root), "no node has both keywords");

          manager.setFilterText("");
          assertEquals(2, model.getChildCount(root), "clearing the box shows everything");
        });
  }

  @Test
  void theTreeIsWideEnoughForItsLongestLabel() throws Exception {
    OptionsTreeManager manager = manager();
    SwingUtilities.invokeAndWait(
        () -> {
          int before = manager.getComponent().getPreferredSize().width;
          manager.addNode(
              ROOT,
              "LONG",
              "A very long label that a narrow tree would cut off in the middle of a word",
              null);
          manager.sizeToFitLabels();
          int after = manager.getComponent().getPreferredSize().width;
          assertTrue(after > before, "the tree grew from " + before + " to " + after);
          assertTrue(after >= 150);
        });
  }
}
