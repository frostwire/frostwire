/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.tabs.Tab;
import com.limegroup.gnutella.gui.GUIMediator.Tabs;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The main window's tabs join it only when first shown. Swing lays out every child of a container
 * in validateTree, hidden cards included, so a window holding every tab laid out all of them when
 * it first appeared: a 2-CPU machine froze for over 5 s showing it (frostwire/frostwire#1515).
 */
class MainFrameLazyTabsTest {

  @Test
  void showingTheWindowAddsOnlyTheSelectedTab() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel cards = new JPanel(new VisibleCardLayout());
          Set<Tabs> added = EnumSet.noneOf(Tabs.class);
          JPanel search = new JPanel();
          JPanel library = new JPanel();
          JPanel transfers = new JPanel();

          assertTrue(MainFrame.showCard(cards, added, Tabs.SEARCH, tab("Search", search)));

          // A tab that isn't in the window is never part of its layout passes.
          assertEquals(1, cards.getComponentCount(), "only the selected tab is in the window");
          assertSame(cards, search.getParent());
          assertNull(library.getParent());
          assertNull(transfers.getParent());

          // The first time the user opens Library, it joins the window and is shown.
          assertTrue(MainFrame.showCard(cards, added, Tabs.LIBRARY, tab("Library", library)));
          assertSame(cards, library.getParent());
          assertTrue(library.isVisible());
          assertFalse(search.isVisible());
          assertNull(transfers.getParent(), "a tab never opened never joins the window");

          // Going back adds nothing twice.
          assertTrue(MainFrame.showCard(cards, added, Tabs.SEARCH, tab("Search", search)));
          assertEquals(2, cards.getComponentCount());
          assertTrue(search.isVisible());
          assertFalse(library.isVisible());
        });
  }

  @Test
  void aTabWithoutAComponentIsNotShown() {
    JPanel cards = new JPanel(new VisibleCardLayout());
    Set<Tabs> added = EnumSet.noneOf(Tabs.class);
    assertFalse(MainFrame.showCard(cards, added, Tabs.LIBRARY, tab("Library", null)));
    assertTrue(added.isEmpty());
    assertEquals(0, cards.getComponentCount());
  }

  @Test
  void mainFrameNoLongerAddsEveryTabUpFront() throws Exception {
    Path source = Path.of("src/main/java/com/limegroup/gnutella/gui/MainFrame.java");
    if (!Files.isRegularFile(source)) {
      source = Path.of("desktop").resolve(source);
    }
    String code =
        new String(Files.readAllBytes(source), StandardCharsets.UTF_8).replaceAll("\\s+", "");
    assertFalse(code.contains("TABBED_PANE.add("), "tabs join the window only through showCard");
    assertTrue(code.contains("showCard(TABBED_PANE,addedCards,tab,t)"));
  }

  private static Tab tab(String title, JComponent component) {
    return (Tab)
        Proxy.newProxyInstance(
            Tab.class.getClassLoader(),
            new Class<?>[] {Tab.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getTitle" -> title;
                  case "getComponent" -> component;
                  default -> null;
                });
  }
}
