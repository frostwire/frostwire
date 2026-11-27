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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPopupMenu;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The search box component: modes, buttons, escape to clear and the delayed instant search. */
class JXSearchFieldTest {

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

  private interface Check {
    void run(JXSearchField field);
  }

  private static void with(Check check) throws Exception {
    SwingUtilities.invokeAndWait(() -> check.run(new JXSearchField()));
  }

  @Test
  void aNewFieldSearchesInstantlyAndHasTheStandardDelay() throws Exception {
    with(
        field -> {
          assertTrue(field.isInstantSearchMode());
          assertFalse(field.isRegularSearchMode());
          assertEquals(180, field.getInstantSearchDelay());
          assertNull(field.getFindPopupMenu());
        });
  }

  @Test
  void theSearchModeAndDelayCanBeChangedAndAnnounceIt() throws Exception {
    with(
        field -> {
          List<String> changes = new ArrayList<>();
          field.addPropertyChangeListener(
              e -> {
                if (e.getPropertyName().equals("searchMode")
                    || e.getPropertyName().equals("instantSearchDelay")) {
                  changes.add(e.getPropertyName() + "=" + e.getNewValue());
                }
              });
          field.setSearchMode(JXSearchField.SearchMode.REGULAR);
          field.setInstantSearchDelay(500);
          assertTrue(field.isRegularSearchMode());
          assertEquals(500, field.getInstantSearchDelay());
          assertEquals(List.of("searchMode=REGULAR", "instantSearchDelay=500"), changes);
        });
  }

  @Test
  void theLayoutStyleIsSetByTheUiProperty() throws Exception {
    with(
        field -> {
          field.customSetUIProperty("layoutStyle", "MAC");
          assertTrue(field.isMacLayoutStyle());
          assertFalse(field.isVistaLayoutStyle());
          field.customSetUIProperty("layoutStyle", "VISTA");
          assertTrue(field.isVistaLayoutStyle(), "set from the UI until the code sets it");
        });
  }

  @Test
  void uiPropertiesThatAreUnknownAreRefused() throws Exception {
    with(
        field ->
            assertThrows(
                IllegalArgumentException.class, () -> field.customSetUIProperty("color", "red")));
  }

  @Test
  void theSeparatePopupButtonAndPromptStyleComeFromTheUiToo() throws Exception {
    with(
        field -> {
          field.customSetUIProperty("useSeperatePopupButton", Boolean.TRUE);
          assertTrue(field.isUseSeperatePopupButton());
          field.customSetUIProperty("promptFontStyle", java.awt.Font.ITALIC);
          assertNotNull(field.getCancelButton());
          assertNotNull(field.getFindButton());
          assertNotNull(field.getPopupButton());
        });
  }

  @Test
  void disablingTheFieldDisablesItsButtons() throws Exception {
    with(
        field -> {
          field.setEnabled(false);
          assertFalse(field.getCancelButton().isEnabled());
          assertFalse(field.getFindButton().isEnabled());
          assertFalse(field.getPopupButton().isEnabled());
          field.setEnabled(true);
          assertTrue(field.getCancelButton().isEnabled());
          assertTrue(field.getFindButton().isEnabled());
          field.setEditable(false);
          assertFalse(field.getCancelButton().isEnabled(), "nothing to clear in a read-only field");
          assertTrue(field.getFindButton().isEnabled());
        });
  }

  @Test
  void theCancelButtonClearsTheText() throws Exception {
    with(
        field -> {
          field.setText("something typed");
          field.getCancelButton().doClick();
          assertEquals("", field.getText());
        });
  }

  @Test
  void escapeClearsTheText() throws Exception {
    with(
        field -> {
          field.setText("something typed");
          KeyEvent escape =
              new KeyEvent(
                  field, KeyEvent.KEY_PRESSED, 1L, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
          for (KeyListener listener : field.getKeyListeners()) {
            listener.keyPressed(escape);
          }
          assertEquals("", field.getText());
          field.setText("kept");
          KeyEvent other = new KeyEvent(field, KeyEvent.KEY_PRESSED, 1L, 0, KeyEvent.VK_A, 'a');
          for (KeyListener listener : field.getKeyListeners()) {
            listener.keyPressed(other);
          }
          assertEquals("kept", field.getText());
        });
  }

  @Test
  void aPopupMenuIsKeptOnTheField() throws Exception {
    with(
        field -> {
          JPopupMenu menu = new JPopupMenu();
          field.setFindPopupMenu(menu);
          assertSame(menu, field.getFindPopupMenu());
          field.updateUI();
          assertFalse(field.hasFocus());
        });
  }

  @Test
  void postingTheActionFiresTheListenersAndStopsTheInstantSearchTimer() throws Exception {
    with(
        field -> {
          List<String> fired = new ArrayList<>();
          field.addActionListener(e -> fired.add(e.getActionCommand()));
          field.setText("query");
          field.getInstantSearchTimer().start();
          assertTrue(field.getInstantSearchTimer().isRunning());
          field.postActionEvent();
          assertFalse(field.getInstantSearchTimer().isRunning());
          assertEquals(List.of("query"), fired);
          assertFalse(field.getInstantSearchTimer().isRepeats());
        });
  }

  @Test
  void theFindActionOnlyPostsInRegularModeFromTheFocusOwner() throws Exception {
    with(
        field -> {
          List<String> fired = new ArrayList<>();
          field.addActionListener(e -> fired.add("fired"));
          field.setSearchMode(JXSearchField.SearchMode.REGULAR);
          field.setText("abc");
          field.getFindButton().doClick();
          assertTrue(fired.isEmpty(), "a field that is not the focus owner posts nothing");
          assertEquals("abc", field.getSelectedText(), "the text is selected");
        });
  }
}
