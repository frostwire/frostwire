/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.trees.FilteredTreeModel;
import java.awt.*;
import java.util.Enumeration;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.limewire.util.I18NConvert;
import org.limewire.util.StringUtils;

/**
 * Manages the <code>JTree</code> instance of the options window. This class constructs the
 * `TreeModel` and forwards many method calls the contained `TreeModel`.
 *
 * <p>
 *
 * <p>In addition, this class controls the `Component` that contains the `JTree` instance and
 * provides access to that `Component`.
 */
public final class OptionsTreeManager {
  /** Handle to the main `JScrollPane` instance for the main window that contains the `JTree`. */
  private final JScrollPane SCROLL_PANE;

  /** Handle to the main `JTree` instance that displays the options. */
  private final JTree TREE;

  /** Constant handle to the tree model. */
  private final OptionsTreeModel TREE_MODEL = new OptionsTreeModel();

  private final FilteredTreeModel FILTERED_TREE_MODEL = new FilteredTreeModel(TREE_MODEL, true);

  /** The constructor constructs the `JTree`, the `TreeModel`, and the `JScrollPane`. */
  OptionsTreeManager() {
    TREE = new JTree();
    boolean opaquePanel = ThemeMediator.isLightThemeOn();
    TREE.setOpaque(opaquePanel);
    TREE.setEditable(false);
    TREE.setShowsRootHandles(true);
    TREE.setRootVisible(false);
    TREE.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    TREE.addTreeSelectionListener(new OptionsTreeSelectionListener(TREE));
    // TREE.setCellRenderer(new LimeTreeCellRenderer());
    TREE.setModel(FILTERED_TREE_MODEL);
    SCROLL_PANE = new JScrollPane(TREE);
    SCROLL_PANE.setOpaque(opaquePanel);
    SCROLL_PANE.setPreferredSize(new Dimension(150, 2000));
    SCROLL_PANE.setMinimumSize(new Dimension(150, 300));
  }

  /**
   * Adds a new child node to one of the top-level parent nodes. children. Neither key can denote
   * the root.
   *
   * <p>
   *
   * <p>This should only be called during tree construction.
   *
   * @param parentKey the unique identifying key of the node to add as well as the key for the
   *     locale-specific name for the node as it appears to the user
   * @param childKey the unique identifying key of the child node to add as well as the key for the
   *     locale-specific name for the node as it appears to the user
   * @param displayName the name of the new node as it is displayed to the user
   * @param keywordText
   * @return
   */
  final OptionsTreeNode addNode(
      final String parentKey, final String childKey, final String displayName, String keywordText) {
    OptionsTreeNode node = TREE_MODEL.addNode(parentKey, childKey, displayName);
    assert (node != null);
    if (keywordText != null) {
      String[] keywords =
          StringUtils.split(I18NConvert.instance().getNorm(keywordText), " \".,\';:()[]");
      for (String keyword : keywords) {
        FILTERED_TREE_MODEL.addSearchKey(node, keyword);
      }
    }
    return node;
  }

  /**
   * Returns the main <code>Component</code> for this class.
   *
   * @return a <code>Component</code> instance that is the main component for this class
   */
  final Component getComponent() {
    return SCROLL_PANE;
  }

  /** Size the navigation for every localized label, even if its branch is collapsed. */
  void sizeToFitLabels() {
    int indent = 20;
    if (TREE.getUI() instanceof BasicTreeUI) {
      BasicTreeUI ui = (BasicTreeUI) TREE.getUI();
      indent = ui.getLeftChildIndent() + ui.getRightChildIndent();
    }
    Insets treeInsets = TREE.getInsets();
    int contentWidth = 0;
    OptionsTreeNode root = (OptionsTreeNode) TREE_MODEL.getRoot();
    for (Enumeration<?> nodes = root.breadthFirstEnumeration(); nodes.hasMoreElements(); ) {
      OptionsTreeNode node = (OptionsTreeNode) nodes.nextElement();
      if (node == root) {
        continue;
      }
      Component renderer =
          TREE.getCellRenderer()
              .getTreeCellRendererComponent(TREE, node, false, false, node.isLeaf(), 0, false);
      // The root is hidden, but root handles still take one level of indentation.
      int rowWidth = renderer.getPreferredSize().width + node.getLevel() * indent;
      contentWidth = Math.max(contentWidth, rowWidth);
    }
    Insets scrollInsets = SCROLL_PANE.getInsets();
    int width =
        Math.max(
            150,
            contentWidth
                + treeInsets.left
                + treeInsets.right
                + scrollInsets.left
                + scrollInsets.right
                + SCROLL_PANE.getVerticalScrollBar().getPreferredSize().width
                + 12);
    SCROLL_PANE.setPreferredSize(new Dimension(width, 2000));
    SCROLL_PANE.setMinimumSize(new Dimension(width, 300));
  }

  /** Sets the selection of the tree to the node with the given key. */
  void setSelection(final String key) {
    if (key == null) return;
    OptionsTreeNode root = (OptionsTreeNode) TREE_MODEL.getRoot();
    OptionsTreeNode node = null;
    for (Enumeration<?> en = root.breadthFirstEnumeration(); en.hasMoreElements(); ) {
      node = (OptionsTreeNode) en.nextElement();
      if (key.equals(node.getTitleKey())) {
        //  set selection and return
        TreePath tp = new TreePath(node.getPath());
        TREE.expandPath(tp);
        TREE.scrollPathToVisible(tp);
        TREE.setSelectionPath(tp);
        return;
      }
    }
  }

  void setFilterText(String text) {
    TreePath path = TREE.getSelectionPath();
    boolean collapsed = TREE.isCollapsed(path);
    FILTERED_TREE_MODEL.filterByText(text);
    if (text.length() > 0) {
      expandAllNodes();
    } else {
      collapseAllNodes();
    }
    if (path != null) {
      TREE.setSelectionPath(path);
      if (text.length() == 0) {
        // special casing to restore tree state if search field is
        // clicked for the first time
        if (collapsed) {
          TREE.collapsePath(path);
        } else {
          TREE.expandPath(path);
        }
      }
      TREE.scrollPathToVisible(path);
    }
  }

  private void expandAllNodes() {
    for (int i = TREE_MODEL.getChildCount(TREE_MODEL.getRoot()) - 1; i >= 0; i--) {
      Object[] path =
          new Object[] {TREE_MODEL.getRoot(), TREE_MODEL.getChild(TREE_MODEL.getRoot(), i)};
      TREE.expandPath(new TreePath(path));
    }
  }

  private void collapseAllNodes() {
    for (int i = TREE_MODEL.getChildCount(TREE_MODEL.getRoot()) - 1; i >= 0; i--) {
      Object[] path =
          new Object[] {TREE_MODEL.getRoot(), TREE_MODEL.getChild(TREE_MODEL.getRoot(), i)};
      TREE.collapsePath(new TreePath(path));
    }
  }
}
