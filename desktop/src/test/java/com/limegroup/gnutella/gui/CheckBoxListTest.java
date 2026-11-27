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

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The checkbox list used by the dialogs: which items are checked, enabled and listed. */
class CheckBoxListTest {

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

  private static final CheckBoxList.TextProvider<String> TEXT =
      new CheckBoxList.TextProvider<>() {
        @Override
        public String getText(String obj) {
          return obj;
        }

        @Override
        public String getToolTipText(String obj) {
          return "tip " + obj;
        }

        @Override
        public Icon getIcon(String obj) {
          return null;
        }
      };

  private static CheckBoxList<String> list(boolean checkAll, String... items) throws Exception {
    java.util.concurrent.atomic.AtomicReference<CheckBoxList<String>> holder =
        new java.util.concurrent.atomic.AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () ->
            holder.set(
                new CheckBoxList<>(List.of(items), TEXT, checkAll, CheckBoxList.SELECT_FIRST_OFF)));
    return holder.get();
  }

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }

  @Test
  void checkAllChecksEveryElementAndNoneChecksNone() throws Exception {
    CheckBoxList<String> all = list(true, "a", "b", "c");
    assertEquals(Set.of("a", "b", "c"), Set.copyOf(all.getCheckedElements()));
    assertTrue(all.getUncheckedElements().isEmpty());
    CheckBoxList<String> none = list(false, "a", "b", "c");
    assertTrue(none.getCheckedElements().isEmpty());
    assertEquals(Set.of("a", "b", "c"), Set.copyOf(none.getUncheckedElements()));
    assertEquals(Set.of("a", "b", "c"), none.getElements());
  }

  @Test
  void itemsCanBeCheckedAndUncheckedInBulk() throws Exception {
    CheckBoxList<String> l = list(false, "a", "b", "c");
    onEdt(
        () -> {
          l.setItemsChecked(Set.of("a", "c"), true);
          assertEquals(Set.of("a", "c"), Set.copyOf(l.getCheckedElements()));
          assertEquals(List.of("b"), l.getUncheckedElements());
          l.setItemsChecked(Set.of("a"), false);
          assertEquals(List.of("c"), l.getCheckedElements());
          assertEquals(Set.of("a", "b"), Set.copyOf(l.getUncheckedElements()));
        });
  }

  @Test
  void aDisabledItemIsNeverReportedAsChecked() throws Exception {
    CheckBoxList<String> l = list(true, "a", "b");
    onEdt(
        () -> {
          l.setItemsEnabled(Set.of("a"), false);
          assertEquals(List.of("b"), l.getCheckedElements());
          assertEquals(List.of("a"), l.getUncheckedElements());
          // enabling it again leaves it unchecked: the user has to opt in
          l.setItemsEnabled(Set.of("a"), true);
          assertEquals(List.of("b"), l.getCheckedElements());
          assertEquals(List.of("a"), l.getUncheckedElements());
        });
  }

  @Test
  void aDisabledListReportsNothingCheckedAndEverythingUnchecked() throws Exception {
    CheckBoxList<String> l = list(true, "a", "b");
    onEdt(
        () -> {
          assertTrue(l.isEnabled());
          l.setEnabled(false);
          assertFalse(l.isEnabled());
          assertTrue(l.getCheckedElements().isEmpty());
          assertEquals(Set.of("a", "b"), Set.copyOf(l.getUncheckedElements()));
          l.setEnabled(true);
          assertEquals(2, l.getCheckedElements().size());
        });
  }

  @Test
  void checkingAnItemBoldsItBriefly() throws Exception {
    CheckBoxList<String> l = list(false, "a", "b");
    onEdt(
        () -> {
          assertTrue(l.setItemChecked("b"));
          assertEquals(List.of("b"), l.getCheckedElements());
          assertThrows(IllegalArgumentException.class, () -> l.setItemChecked(null));
          assertThrows(IllegalArgumentException.class, () -> l.setItemSelected(null));
          l.setItemSelected("a");
          l.update();
        });
  }

  @Test
  void replacingTheElementsStartsOverWithEverythingChecked() throws Exception {
    CheckBoxList<String> l = list(false, "a");
    onEdt(
        () -> {
          l.setElements(List.of("x", "y"));
          assertEquals(Set.of("x", "y"), l.getElements());
          assertEquals(Set.of("x", "y"), Set.copyOf(l.getCheckedElements()));
        });
  }

  @Test
  void selectAllAndDeselectAllMoveEverythingAtOnce() throws Exception {
    CheckBoxList<String> l = list(false, "a", "b");
    Action[] actions = l.getActions();
    assertEquals(2, actions.length);
    onEdt(
        () -> {
          actions[0].actionPerformed(null);
          assertEquals(2, l.getCheckedElements().size());
          actions[1].actionPerformed(null);
          assertTrue(l.getCheckedElements().isEmpty());
          assertEquals(2, l.getUncheckedElements().size());
        });
    assertFalse(String.valueOf(actions[0].getValue(Action.NAME)).isEmpty());
    assertFalse(String.valueOf(actions[1].getValue(Action.SHORT_DESCRIPTION)).isEmpty());
  }

  @Test
  void aTextProviderIsRequired() throws Exception {
    CheckBoxList<String> l = list(true, "a");
    assertThrows(NullPointerException.class, () -> l.setTextProvider(null));
    assertThrows(
        NullPointerException.class,
        () -> new CheckBoxList<String>(List.of("a"), null, true, CheckBoxList.SELECT_FIRST_OFF));
    l.setTextProvider(TEXT);
  }

  @Test
  void visibleRowCountSizesThePanelByRowHeight() throws Exception {
    CheckBoxList<String> l = list(true, "a", "b", "c", "d");
    onEdt(
        () -> {
          l.setVisibleRowCount(2);
          assertEquals(22 * 2, l.getPreferredSize().height);
        });
  }

  @Test
  void checkBoxListEventsCarryTheirItem() {
    CheckBoxList.CheckBoxListSelectionEvent selection =
        new CheckBoxList.CheckBoxListSelectionEvent("src", "item");
    assertEquals("item", selection.getSelected());
    CheckBoxList.CheckBoxListCheckChangeEvent<String> change =
        new CheckBoxList.CheckBoxListCheckChangeEvent<>("src", "item", true);
    assertEquals("item", change.getSelected());
    assertTrue(change.getChecked());
  }
}
