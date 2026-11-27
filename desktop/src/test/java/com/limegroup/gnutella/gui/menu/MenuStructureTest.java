/*
 *     Created by Angel Leon (@gubatron)
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

package com.limegroup.gnutella.gui.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The menu bar of the main window: its menus, their items and mnemonics. */
class MenuStructureTest {

  private static LookAndFeel previous;

  @BeforeAll
  static void metal() throws Exception {
    System.setProperty("java.awt.headless", "true");
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new MetalLookAndFeel());
  }

  @AfterAll
  static void restore() throws Exception {
    UIManager.setLookAndFeel(previous);
  }

  private static JMenuBar bar() throws Exception {
    JMenuBar[] holder = new JMenuBar[1];
    SwingUtilities.invokeAndWait(() -> holder[0] = MenuMediator.instance().getMenuBar());
    return holder[0];
  }

  private static List<String> itemTexts(JMenu menu) {
    List<String> texts = new ArrayList<>();
    for (int i = 0; i < menu.getItemCount(); i++) {
      JMenuItem item = menu.getItem(i);
      texts.add(item == null ? "-" : item.getText());
    }
    return texts;
  }

  @Test
  void theBarHasFileViewToolsAndHelpInThatOrder() throws Exception {
    JMenuBar bar = bar();
    assertEquals(4, bar.getMenuCount());
    assertEquals("File", bar.getMenu(0).getText());
    assertEquals("View", bar.getMenu(1).getText());
    assertNotNull(bar.getMenu(2).getText());
    assertNotNull(bar.getMenu(3).getText());
  }

  @Test
  void theMenuBarIsASingleton() throws Exception {
    assertSame(bar(), bar());
  }

  @Test
  void everyMenuHasAMnemonicFromItsAmpersand() throws Exception {
    JMenuBar bar = bar();
    assertEquals(KeyEvent.VK_F, bar.getMenu(0).getMnemonic());
    assertEquals(KeyEvent.VK_V, bar.getMenu(1).getMnemonic());
    for (int i = 0; i < bar.getMenuCount(); i++) {
      assertTrue(bar.getMenu(i).getMnemonic() != 0, "menu " + i);
    }
  }

  @Test
  void theFileMenuOffersSendOpenAndCreate() throws Exception {
    List<String> items = itemTexts(bar().getMenu(0));
    assertTrue(items.contains("Send File or Folder..."), items.toString());
    assertTrue(items.contains("Create New Torrent"), items.toString());
    assertTrue(items.stream().anyMatch(t -> t.contains(".Torrent")), items.toString());
  }

  @Test
  void theViewMenuOffersTheDisplayToggles() throws Exception {
    JMenu view = bar().getMenu(1);
    List<String> items = itemTexts(view);
    assertTrue(items.contains("Use Small Icons"), items.toString());
    assertTrue(items.contains("Show Icon Text"), items.toString());
    assertTrue(items.contains("Increase Font Size"), items.toString());
    assertTrue(items.contains("Decrease Font Size"), items.toString());
    assertTrue(items.contains("Change Language"), items.toString());
    JMenuItem increase = view.getItem(items.indexOf("Increase Font Size"));
    assertNotNull(increase.getAccelerator());
    assertEquals(KeyEvent.VK_PLUS, increase.getAccelerator().getKeyCode());
  }

  @Test
  void theToolsAndHelpMenusAreNotEmptyAndEveryItemHasAnAction() throws Exception {
    JMenuBar bar = bar();
    for (int m = 2; m < 4; m++) {
      JMenu menu = bar.getMenu(m);
      assertTrue(menu.getItemCount() > 0, "menu " + m);
      for (int i = 0; i < menu.getItemCount(); i++) {
        JMenuItem item = menu.getItem(i);
        if (item != null) {
          if (item instanceof JMenu submenu) {
            assertTrue(submenu.getItemCount() > 0, "submenu " + submenu.getText());
          } else {
            assertNotNull(item.getAction(), menu.getText() + ": " + item.getText());
          }
          assertFalse(item.getText().isEmpty());
        }
      }
    }
  }
}
