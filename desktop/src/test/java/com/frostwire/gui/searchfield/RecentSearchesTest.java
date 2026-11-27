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

package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The recent searches menu of the search field: the last five distinct searches, newest first, with
 * a clear item. The list is built without a save name so nothing reaches the user's preferences.
 */
class RecentSearchesTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static RecentSearches unsaved() {
    return new RecentSearches((String) null);
  }

  private static void search(RecentSearches recent, String text) {
    recent.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, text));
  }

  private static JTextField installed(RecentSearches recent) {
    JTextField field = new JTextField();
    recent.install(field);
    return field;
  }

  @Test
  void aNewListOffersOnlyADisabledNoRecentsItem() {
    JTextField field = installed(unsaved());
    JPopupMenu menu = NativeSearchFieldSupport.getFindPopupMenu(field);
    assertNotNull(menu);
    assertEquals(1, menu.getComponentCount());
    assertFalse(((JMenuItem) menu.getComponent(0)).isEnabled());
  }

  @Test
  void searchesAreListedNewestFirstWithAClearItem() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    search(recent, "first");
    search(recent, "second");
    JPopupMenu menu = NativeSearchFieldSupport.getFindPopupMenu(field);
    assertEquals("second", ((JMenuItem) menu.getComponent(1)).getText());
    assertEquals("first", ((JMenuItem) menu.getComponent(2)).getText());
    assertTrue(menu.getComponent(3) instanceof JPopupMenu.Separator);
    assertTrue(menu.getComponent(4) instanceof JMenuItem);
  }

  @Test
  void aRepeatedSearchMovesToTheTopInsteadOfDuplicating() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    search(recent, "a");
    search(recent, "b");
    search(recent, "a");
    JPopupMenu menu = NativeSearchFieldSupport.getFindPopupMenu(field);
    assertEquals("a", ((JMenuItem) menu.getComponent(1)).getText());
    assertEquals("b", ((JMenuItem) menu.getComponent(2)).getText());
    assertTrue(menu.getComponent(3) instanceof JPopupMenu.Separator);
  }

  @Test
  void blankAndNullSearchesAreIgnored() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    search(recent, null);
    search(recent, "");
    search(recent, "   ");
    assertEquals(1, NativeSearchFieldSupport.getFindPopupMenu(field).getComponentCount());
  }

  @Test
  void onlyTheLastFiveSearchesAreKept() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    for (int i = 1; i <= 7; i++) {
      search(recent, "s" + i);
    }
    JPopupMenu menu = NativeSearchFieldSupport.getFindPopupMenu(field);
    // title, five searches, separator, clear
    assertEquals(8, menu.getComponentCount());
    assertEquals("s7", ((JMenuItem) menu.getComponent(1)).getText());
    assertEquals("s3", ((JMenuItem) menu.getComponent(5)).getText());
  }

  @Test
  void pickingARecentSearchFillsTheFieldAndFiresItsAction() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    search(recent, "ubuntu");
    List<String> fired = new ArrayList<>();
    field.addActionListener(e -> fired.add(e.getActionCommand()));
    JMenuItem item = (JMenuItem) NativeSearchFieldSupport.getFindPopupMenu(field).getComponent(1);
    item.doClick();
    assertEquals("ubuntu", field.getText());
    assertEquals(List.of("ubuntu"), fired);
  }

  @Test
  void theClearItemEmptiesTheList() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    search(recent, "x");
    JPopupMenu menu = NativeSearchFieldSupport.getFindPopupMenu(field);
    ((JMenuItem) menu.getComponent(menu.getComponentCount() - 1)).doClick();
    assertEquals(1, menu.getComponentCount());
    assertFalse(((JMenuItem) menu.getComponent(0)).isEnabled());
  }

  @Test
  void theFieldItselfFeedsTheListWhenItsActionFires() {
    RecentSearches recent = unsaved();
    JTextField field = installed(recent);
    field.setText("from the field");
    field.postActionEvent();
    JPopupMenu menu = NativeSearchFieldSupport.getFindPopupMenu(field);
    assertEquals("from the field", ((JMenuItem) menu.getComponent(1)).getText());
  }

  @Test
  void nativeSearchFieldPropertiesAreStoredOnTheField() {
    JTextField field = new JTextField();
    assertFalse(NativeSearchFieldSupport.isSearchField(field));
    NativeSearchFieldSupport.setSearchField(field, true);
    assertTrue(NativeSearchFieldSupport.isSearchField(field));
    assertFalse(
        NativeSearchFieldSupport.isNativeSearchField(field),
        "no native search field support on this toolkit");
    NativeSearchFieldSupport.setSearchField(field, true);
    assertTrue(NativeSearchFieldSupport.isSearchField(field));
    NativeSearchFieldSupport.setSearchField(field, false);
    assertFalse(NativeSearchFieldSupport.isSearchField(field));

    ActionListener find = e -> {};
    ActionListener cancel = e -> {};
    assertNull(NativeSearchFieldSupport.getFindAction(field));
    NativeSearchFieldSupport.setFindAction(field, find);
    NativeSearchFieldSupport.setCancelAction(field, cancel);
    assertSame(find, NativeSearchFieldSupport.getFindAction(field));
    assertSame(cancel, NativeSearchFieldSupport.getCancelAction(field));
    JPopupMenu popup = new JPopupMenu();
    NativeSearchFieldSupport.setFindPopupMenu(field, popup);
    assertSame(popup, NativeSearchFieldSupport.getFindPopupMenu(field));
  }
}
