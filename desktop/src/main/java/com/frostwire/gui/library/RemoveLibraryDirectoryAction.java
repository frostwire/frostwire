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

package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.actions.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/** Removes the selected root folder from a {@link RecursiveLibraryDirectoryPanel}. */
public class RemoveLibraryDirectoryAction extends AbstractAction {
  private final JTree tree;
  private final Predicate<File> isRoot;
  private final Consumer<File> removeRoot;

  public RemoveLibraryDirectoryAction(RecursiveLibraryDirectoryPanel recursiveSharingPanel) {
    this(
        recursiveSharingPanel.getTree(),
        recursiveSharingPanel::isRoot,
        recursiveSharingPanel::removeRoot);
  }

  RemoveLibraryDirectoryAction(JTree tree, Predicate<File> isRoot, Consumer<File> removeRoot) {
    super(I18n.tr("Remove"));
    this.tree = tree;
    this.isRoot = isRoot;
    this.removeRoot = removeRoot;
    setEnabled(false);
    tree.addTreeSelectionListener(new EnablementSelectionListener());
  }

  public void actionPerformed(ActionEvent e) {
    File root = selectedRoot();
    if (root != null) {
      removeRoot.accept(root);
    }
  }

  /**
   * The root folder that is selected now, or null. Both the button's state and the removal go by
   * this: the state used to follow {@link TreeSelectionEvent#getPath()}, which is the path that was
   * added to or removed from the selection, so deselecting a root left Remove enabled with nothing
   * selected, and clicking it threw NullPointerException (frostwire/frostwire#1528).
   */
  private File selectedRoot() {
    TreePath selection = tree.getSelectionPath();
    if (selection == null || !(selection.getLastPathComponent() instanceof File folder)) {
      return null;
    }
    return isRoot.test(folder) ? folder : null;
  }

  /** Enables the action while a root folder is selected. */
  private class EnablementSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      setEnabled(selectedRoot() != null);
    }
  }
}
