/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.tables.DefaultMouseListener;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

/**
 * Double-clicking an entry of the Library's left-hand tree refreshes it, like F5 does; a
 * double-click on the empty space below the entries doesn't.
 */
class LibraryTreeDoubleClickRefreshTest {

  @Test
  void doubleClickingAnEntryRefreshesIt() throws Exception {
    AtomicInteger refreshes = new AtomicInteger();
    SwingUtilities.invokeAndWait(
        () -> {
          JTree tree = tree();
          DefaultMouseListener listener =
              new DefaultMouseListener(
                  new TreeMouseObserver(tree, new JPopupMenu(), refreshes::incrementAndGet));
          Rectangle row = tree.getRowBounds(1);
          int x = row.x + 2;
          int y = row.y + row.height / 2;

          listener.mouseClicked(click(tree, x, y, 1));
          assertEquals(0, refreshes.get(), "a single click only selects");
          listener.mouseClicked(click(tree, x, y, 2));
          assertEquals(1, refreshes.get(), "a double-click on an entry refreshes it");

          int below = tree.getRowBounds(tree.getRowCount() - 1).y + 200;
          listener.mouseClicked(click(tree, x, below, 1));
          listener.mouseClicked(click(tree, x, below, 2));
          assertEquals(1, refreshes.get(), "a double-click below the entries does nothing");
        });
  }

  @Test
  void theLibraryExplorerRefreshesLikeF5OnADoubleClick() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/frostwire/gui/library/LibraryExplorer.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(source.contains("newTreeMouseObserver(tree,popup,()->refreshSelection(true))"));
  }

  private static JTree tree() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    root.add(new DefaultMutableTreeNode("Audio"));
    root.add(new DefaultMutableTreeNode("Video"));
    JTree tree = new JTree(new DefaultTreeModel(root));
    tree.setRootVisible(false);
    tree.setSize(200, 400);
    tree.doLayout();
    return tree;
  }

  private static MouseEvent click(JTree tree, int x, int y, int count) {
    return new MouseEvent(
        tree,
        MouseEvent.MOUSE_CLICKED,
        System.currentTimeMillis(),
        MouseEvent.BUTTON1_DOWN_MASK,
        x,
        y,
        count,
        false,
        MouseEvent.BUTTON1);
  }
}
