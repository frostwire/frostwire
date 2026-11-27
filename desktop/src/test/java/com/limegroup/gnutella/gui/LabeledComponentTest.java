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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A caption next to or around a component, with glue on the requested side. */
class LabeledComponentTest {

  // The alignments other than LEFT are private to the class; these are its values.
  private static final int RIGHT = 11;
  private static final int TOP_CENTER = 12;
  private static final int TOP_LEFT = 13;
  private static final int TOP_RIGHT = 14;
  private static final int BOTTOM_CENTER = 15;
  private static final int BOTTOM_LEFT = 16;
  private static final int BOTTOM_RIGHT = 17;

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

  private static boolean isGlue(Component c) {
    return c instanceof Box.Filler;
  }

  @Test
  void theLabelComesBeforeTheComponentByDefault() throws Exception {
    onEdt(
        () -> {
          JTextField field = new JTextField();
          JComponent panel = new LabeledComponent("Name", field).getComponent();
          assertTrue(panel.getComponent(0) instanceof JLabel);
          assertEquals("Name", ((JLabel) panel.getComponent(0)).getText());
          assertSame(field, panel.getComponent(2));
          assertEquals(BoxLayout.X_AXIS, ((BoxLayout) panel.getLayout()).getAxis());
        });
  }

  @Test
  void theLabelCanFollowTheComponent() throws Exception {
    onEdt(
        () -> {
          JTextField field = new JTextField();
          JComponent panel =
              new LabeledComponent("Name", field, LabeledComponent.NO_GLUE, RIGHT).getComponent();
          assertSame(field, panel.getComponent(0));
          assertTrue(panel.getComponent(2) instanceof JLabel);
        });
  }

  @Test
  void leftGlueAndRightGluePushTheContentTheOtherWay() throws Exception {
    onEdt(
        () -> {
          JTextField field = new JTextField();
          JComponent leftGlue =
              new LabeledComponent("Name", field, LabeledComponent.LEFT_GLUE).getComponent();
          assertTrue(isGlue(leftGlue.getComponent(0)));
          assertEquals(4, leftGlue.getComponentCount());
          JComponent surround =
              new LabeledComponent("Name", field, 140, LabeledComponent.LEFT).getComponent();
          assertTrue(isGlue(surround.getComponent(0)));
          assertTrue(isGlue(surround.getComponent(surround.getComponentCount() - 1)));
          JComponent rightGlue =
              new LabeledComponent("Name", field, 130, LabeledComponent.LEFT).getComponent();
          assertTrue(isGlue(rightGlue.getComponent(rightGlue.getComponentCount() - 1)));
        });
  }

  @Test
  void captionsAboveTheComponentStackVertically() throws Exception {
    onEdt(
        () -> {
          for (int alignment : new int[] {TOP_LEFT, TOP_CENTER, TOP_RIGHT}) {
            JTextField field = new JTextField();
            JComponent panel =
                new LabeledComponent("Caption", field, LabeledComponent.NO_GLUE, alignment)
                    .getComponent();
            assertEquals(BoxLayout.Y_AXIS, ((BoxLayout) panel.getLayout()).getAxis());
            assertTrue(panel.getComponent(0) instanceof JComponent);
            assertSame(field, panel.getComponent(2), "alignment " + alignment);
          }
        });
  }

  @Test
  void captionsBelowTheComponentComeAfterIt() throws Exception {
    onEdt(
        () -> {
          for (int alignment : new int[] {BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT}) {
            JTextField field = new JTextField();
            JComponent panel =
                new LabeledComponent("Caption", field, LabeledComponent.NO_GLUE, alignment)
                    .getComponent();
            assertEquals(BoxLayout.Y_AXIS, ((BoxLayout) panel.getLayout()).getAxis());
            assertSame(field, panel.getComponent(0), "alignment " + alignment);
          }
        });
  }

  @Test
  void anUnknownAlignmentIsRefused() throws Exception {
    onEdt(
        () -> {
          try {
            new LabeledComponent("C", new JTextField(), LabeledComponent.NO_GLUE, 99);
            throw new AssertionError("the alignment 99 was accepted");
          } catch (IllegalArgumentException | AssertionError refused) {
            // the constructor's assert fires with -ea, otherwise the final else does
            assertTrue(
                refused instanceof IllegalArgumentException
                    || !"the alignment 99 was accepted".equals(refused.getMessage()));
          }
        });
  }
}
