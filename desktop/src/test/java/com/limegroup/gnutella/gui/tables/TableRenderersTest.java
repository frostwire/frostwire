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

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.GUIUtils;
import java.awt.Component;
import java.util.Date;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The text and icons the table cell renderers put in each cell. */
class TableRenderersTest {

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

  private static final Icon ICON = new GUIUtils.EmptyIcon("icon", 16, 16);

  @Test
  void speedsAreShownInKilobytesPerSecondAndUnknownOnesAreBlank() throws Exception {
    onEdt(
        () -> {
          SpeedRenderer renderer = new SpeedRenderer();
          JTable table = new JTable(1, 1);
          JLabel fast =
              (JLabel) renderer.getTableCellRendererComponent(table, 2048.0, false, false, 0, 0);
          assertEquals(GUIUtils.rate2speed(2048.0), fast.getText());
          JLabel unknown =
              (JLabel) renderer.getTableCellRendererComponent(table, -1.0, false, false, 0, 0);
          assertEquals("", unknown.getText());
          JLabel none =
              (JLabel) renderer.getTableCellRendererComponent(table, null, false, false, 0, 0);
          assertEquals("", none.getText());
          JLabel zero =
              (JLabel) renderer.getTableCellRendererComponent(table, 0.0, false, false, 0, 0);
          assertEquals(GUIUtils.rate2speed(0.0), zero.getText());
        });
  }

  @Test
  void anIconCellShowsOnlyTheIcon() throws Exception {
    onEdt(
        () -> {
          IconRenderer renderer = new IconRenderer();
          JLabel cell =
              (JLabel)
                  renderer.getTableCellRendererComponent(
                      new JTable(1, 1), ICON, false, false, 0, 0);
          assertSame(ICON, cell.getIcon());
          assertEquals("", cell.getText());
          assertEquals(javax.swing.SwingConstants.CENTER, cell.getHorizontalAlignment());
        });
  }

  private static final class IconAndName implements IconAndNameHolder {
    private final String name;

    IconAndName(String name) {
      this.name = name;
    }

    @Override
    public Icon getIcon() {
      return ICON;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Test
  void anIconAndNameCellShowsBoth() throws Exception {
    onEdt(
        () -> {
          IconAndNameRenderer renderer = new IconAndNameRenderer();
          JTable table = new JTable(1, 1);
          JLabel cell =
              (JLabel)
                  renderer.getTableCellRendererComponent(
                      table, new IconAndName("song.mp3"), false, false, 0, 0);
          assertEquals("song.mp3", cell.getText());
          assertSame(ICON, cell.getIcon());
          JLabel empty =
              (JLabel) renderer.getTableCellRendererComponent(table, null, false, false, 0, 0);
          assertNull(empty.getIcon());
          assertEquals("", empty.getText());
        });
  }

  @Test
  void aNameHolderCellShowsItsName() throws Exception {
    onEdt(
        () -> {
          NameHolderRenderer renderer = new NameHolderRenderer();
          JTable table = new JTable(1, 1);
          Component cell =
              renderer.getTableCellRendererComponent(
                  table, new NameHolder("Some Song"), false, false, 0, 0);
          assertNotNull(cell);
          assertTrue(textOf(cell).contains("Some Song"), textOf(cell));
        });
  }

  private static String textOf(Component component) {
    if (component instanceof JLabel label && label.getText() != null) {
      return label.getText();
    }
    if (component instanceof java.awt.Container container) {
      for (Component child : container.getComponents()) {
        String text = textOf(child);
        if (!text.isEmpty()) {
          return text;
        }
      }
    }
    return "";
  }

  @Test
  void textCellsAreSanitizedBeforeTheyAreShown() throws Exception {
    onEdt(
        () -> {
          DefaultTableBevelledCellRenderer renderer = new DefaultTableBevelledCellRenderer();
          JLabel cell =
              (JLabel)
                  renderer.getTableCellRendererComponent(
                      new JTable(1, 1), "plain text", false, false, 0, 0);
          assertEquals("plain text", cell.getText());
          JLabel notText =
              (JLabel)
                  renderer.getTableCellRendererComponent(new JTable(1, 1), 42, false, false, 0, 0);
          assertEquals("42", notText.getText());
        });
  }

  @Test
  void dateCellsShowDatesAndNumbersAsTheFullDateTime() throws Exception {
    onEdt(
        () -> {
          DateRenderer renderer = new DateRenderer();
          JTable table = new JTable(1, 1);
          String expected = GUIUtils.getFullDateTimeFormat().format(new Date(86_400_000L));
          JLabel fromDate =
              (JLabel)
                  renderer.getTableCellRendererComponent(
                      table, new Date(86_400_000L), false, false, 0, 0);
          assertEquals(expected, fromDate.getText());
          JLabel fromMillis =
              (JLabel)
                  renderer.getTableCellRendererComponent(table, 86_400_000L, false, false, 0, 0);
          assertEquals(expected, fromMillis.getText());
          JLabel none =
              (JLabel) renderer.getTableCellRendererComponent(table, null, false, false, 0, 0);
          assertEquals("", none.getText());
        });
  }
}
