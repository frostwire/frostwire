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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The row of buttons under the tables: labels, tooltips, listeners, enabling and glue. */
class ButtonRowTest {

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

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }

  private static int count(ButtonRow row, Class<?> type) {
    int n = 0;
    for (Component c : row.getComponents()) {
      if (type.isInstance(c)) n++;
    }
    return n;
  }

  @Test
  void buttonsGetTheirLabelsTooltipsAndListeners() throws Exception {
    onEdt(
        () -> {
          List<String> clicked = new ArrayList<>();
          ButtonRow row =
              new ButtonRow(
                  new String[] {"Play", ""},
                  new String[] {"Plays it", null},
                  new EventListener[] {
                    (ActionListener) e -> clicked.add("play"),
                    (ActionListener) e -> clicked.add("other")
                  },
                  BoxLayout.X_AXIS,
                  ButtonRow.NO_GLUE);
          assertEquals("Play", row.getButtonAtIndex(0).getText());
          assertEquals("Plays it", row.getButtonAtIndex(0).getToolTipText());
          assertEquals("", row.getButtonAtIndex(1).getText());
          assertNull(row.getButtonAtIndex(1).getToolTipText());
          row.getButtonAtIndex(1).doClick();
          row.getButtonAtIndex(0).doClick();
          assertEquals(List.of("other", "play"), clicked);
        });
  }

  @Test
  void aMismatchBetweenLabelsTooltipsAndListenersIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ButtonRow(
                new String[] {"a", "b"},
                new String[] {"a"},
                new EventListener[] {(ActionListener) e -> {}, (ActionListener) e -> {}},
                BoxLayout.X_AXIS,
                ButtonRow.NO_GLUE));
  }

  @Test
  void buttonsCanBeEnabledOneByOneOrAllAtOnce() throws Exception {
    onEdt(
        () -> {
          ButtonRow row =
              new ButtonRow(
                  new String[] {"a", "b", "c"},
                  new String[] {null, null, null},
                  new EventListener[] {
                    (ActionListener) e -> {}, (ActionListener) e -> {}, (ActionListener) e -> {}
                  },
                  BoxLayout.X_AXIS,
                  ButtonRow.LEFT_GLUE);
          row.setButtonEnabled(1, false);
          assertTrue(row.getButtonAtIndex(0).isEnabled());
          assertFalse(row.getButtonAtIndex(1).isEnabled());
          row.setButtonsEnabled(false);
          for (int i = 0; i < 3; i++) {
            assertFalse(row.getButtonAtIndex(i).isEnabled());
          }
          row.setButtonsEnabled(true);
          assertTrue(row.getButtonAtIndex(2).isEnabled());
          assertThrows(ArrayIndexOutOfBoundsException.class, () -> row.getButtonAtIndex(3));
          assertThrows(ArrayIndexOutOfBoundsException.class, () -> row.setButtonEnabled(3, true));
        });
  }

  @Test
  void glueSitsOnTheRequestedSide() throws Exception {
    onEdt(
        () -> {
          ActionListener noop = e -> {};
          ButtonRow left = row(ButtonRow.LEFT_GLUE, noop);
          ButtonRow right = row(ButtonRow.RIGHT_GLUE, noop);
          ButtonRow none = row(ButtonRow.NO_GLUE, noop);
          assertTrue(left.getComponent(0) instanceof Box.Filler);
          assertTrue(left.getComponent(left.getComponentCount() - 1) instanceof JButton);
          assertTrue(right.getComponent(0) instanceof JButton);
          assertTrue(right.getComponent(right.getComponentCount() - 1) instanceof Box.Filler);
          assertTrue(none.getComponent(0) instanceof Box.Filler);
          assertTrue(none.getComponent(none.getComponentCount() - 1) instanceof Box.Filler);
          assertEquals(2, count(left, JButton.class));
        });
  }

  private static ButtonRow row(int glue, ActionListener listener) {
    return new ButtonRow(
        new String[] {"a", "b"},
        new String[] {null, null},
        new EventListener[] {listener, listener},
        BoxLayout.X_AXIS,
        glue);
  }

  @Test
  void actionsBecomeButtonsThatRunThem() throws Exception {
    onEdt(
        () -> {
          int[] ran = {0};
          Action act =
              new AbstractAction("Do it") {
                @Override
                public void actionPerformed(ActionEvent e) {
                  ran[0]++;
                }
              };
          Action disabled =
              new AbstractAction("Can't") {
                @Override
                public void actionPerformed(ActionEvent e) {}
              };
          disabled.setEnabled(false);
          ButtonRow row =
              new ButtonRow(new Action[] {act, disabled}, BoxLayout.Y_AXIS, ButtonRow.NO_GLUE);
          assertEquals("Do it", row.getButtonAtIndex(0).getText());
          row.getButtonAtIndex(0).doClick();
          assertEquals(1, ran[0]);
          assertFalse(row.getButtonAtIndex(1).isEnabled());
          assertSame(act, row.getButtonAtIndex(0).getAction());
        });
  }

  @Test
  void aRowWithoutButtonsIsEmptyInsteadOfThrowing() throws Exception {
    onEdt(
        () -> {
          ButtonRow fromKeys =
              new ButtonRow(new String[0], new String[0], new EventListener[0], null);
          assertEquals(0, count(fromKeys, JButton.class));
          ButtonRow fromKeysVertical =
              new ButtonRow(
                  new String[0],
                  new String[0],
                  new EventListener[0],
                  BoxLayout.Y_AXIS,
                  ButtonRow.NO_GLUE);
          assertEquals(0, count(fromKeysVertical, JButton.class));
          ButtonRow fromActions = new ButtonRow(new Action[0], BoxLayout.X_AXIS, ButtonRow.NO_GLUE);
          assertEquals(0, count(fromActions, JButton.class));
        });
  }
}
