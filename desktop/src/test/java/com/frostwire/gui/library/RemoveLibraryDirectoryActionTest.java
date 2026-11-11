/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

/**
 * Options → Library: Remove stayed enabled after its root folder was deselected, and clicking it
 * with nothing selected threw NullPointerException (Icebase #d098e16eac57,
 * frostwire/frostwire#1528).
 */
class RemoveLibraryDirectoryActionTest {
  private static final File ROOT = new File("music").getAbsoluteFile();
  private static final File CHILD = new File(ROOT, "album");

  @Test
  void removeFollowsTheCurrentSelectionNotTheLastChangedPath() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JTree tree = new JTree(folders());
          List<File> removed = new ArrayList<>();
          RemoveLibraryDirectoryAction remove =
              new RemoveLibraryDirectoryAction(tree, ROOT::equals, removed::add);
          TreePath root = new TreePath(new Object[] {"folders", ROOT});
          TreePath child = root.pathByAddingChild(CHILD);
          assertFalse(remove.isEnabled(), "nothing is selected yet");

          tree.setSelectionPath(root);
          assertTrue(remove.isEnabled());

          // Deselecting the root reports the root as the changed path, with nothing selected.
          tree.removeSelectionPath(root);
          assertFalse(remove.isEnabled(), "Remove stayed enabled with no folder selected");

          remove.actionPerformed(null);
          assertEquals(List.of(), removed);

          tree.setSelectionPath(child);
          assertFalse(remove.isEnabled(), "only root folders can be removed");

          tree.setSelectionPath(root);
          remove.actionPerformed(null);
          assertEquals(List.of(ROOT), removed);
        });
  }

  /** A model whose nodes are the Files themselves, like the panel's FileTreeModel. */
  private static TreeModel folders() {
    return new DefaultTreeModel(new DefaultMutableTreeNode("folders")) {
      @Override
      public Object getRoot() {
        return "folders";
      }

      @Override
      public int getChildCount(Object parent) {
        return "folders".equals(parent) || ROOT.equals(parent) ? 1 : 0;
      }

      @Override
      public Object getChild(Object parent, int index) {
        return "folders".equals(parent) ? ROOT : CHILD;
      }

      @Override
      public boolean isLeaf(Object node) {
        return CHILD.equals(node);
      }

      @Override
      public int getIndexOfChild(Object parent, Object child) {
        return 0;
      }
    };
  }
}
