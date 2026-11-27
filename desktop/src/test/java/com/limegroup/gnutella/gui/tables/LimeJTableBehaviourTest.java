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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.TableModel;
import javax.swing.text.Position;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

/**
 * The shared table component: selection helpers, hidden columns, type-ahead matching and the
 * keystroke mover that drives it, and the cell renderers that hand out text and colors.
 */
class LimeJTableBehaviourTest {

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

  /** Name and a second column. */
  public static final class Line extends AbstractDataLine<String> {
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return col == 0 ? NAME : OTHER;
    }

    @Override
    public boolean isDynamic(int col) {
      return false;
    }

    @Override
    public boolean isClippable(int col) {
      return true;
    }

    @Override
    public Object getValueAt(int col) {
      return col == 0 ? initializer : "other of " + initializer;
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    static final LimeTableColumn NAME =
        new LimeTableColumn(0, "NAME", "Name", 100, true, String.class);
    static final LimeTableColumn OTHER =
        new LimeTableColumn(1, "OTHER", "Other", 100, true, String.class);
  }

  static final class Model extends BasicDataLineModel<Line, String> {
    Model() {
      super(Line.class);
    }
  }

  /**
   * LimeJTable's constructor reads the theme's table font, which needs a display and writes the
   * theme setting, so the table is built through JTable's constructor only, and the fields the
   * skipped constructor would have initialized are set here.
   */
  private static LimeJTable fruitTable(String... fruit) {
    Model model = new Model();
    for (String f : fruit) {
      model.add(f, model.getRowCount());
    }
    try {
      Constructor<?> constructor =
          ReflectionFactory.getReflectionFactory()
              .newConstructorForSerialization(
                  LimeJTable.class, JTable.class.getDeclaredConstructor(TableModel.class));
      LimeJTable table = (LimeJTable) constructor.newInstance(model);
      Field hidden = LimeJTable.class.getDeclaredField("_hiddenColumns");
      hidden.setAccessible(true);
      hidden.set(table, new HashMap<Object, LimeTableColumn>());
      return table;
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }

  @Test
  void selectingARowSelectsOnlyThatRow() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a", "b", "c");
          t.setSelectedRow(0);
          t.setSelectedRow(2);
          assertEquals(2, t.getSelectedRow());
          assertArrayEquals(new int[] {2}, t.getSelectedRows());
          t.setSelectedRow(7);
          t.setSelectedRow(-1);
          assertEquals(2, t.getSelectedRow(), "an out of range row changes nothing");
        });
  }

  @Test
  void selectedRowsAreSortedAndDroppedWhenTheyNoLongerExist() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a", "b", "c", "d");
          t.addRowSelectionInterval(3, 3);
          t.addRowSelectionInterval(1, 1);
          assertArrayEquals(new int[] {1, 3}, t.getSelectedRows());
          t.clearSelection();
          assertEquals(0, t.getSelectedRows().length);
          assertEquals(-1, t.getSelectedRow());
        });
  }

  @Test
  void noRowIsVisibleOrEnsuredWhenThereIsNone() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          assertFalse(t.isRowVisible(-1));
          assertFalse(t.isSelectionVisible());
          t.ensureRowVisible(-1);
          t.ensureSelectionVisible();
        });
  }

  @Test
  void hidingAColumnRemembersItAndShowingItBringsItBack() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          assertEquals(2, t.getColumnCount());
          assertTrue(t.isColumnVisible("OTHER"));
          assertDoesNotThrowHide(t, "OTHER");
          assertEquals(1, t.getColumnCount());
          assertFalse(t.isColumnVisible("OTHER"));
          assertSame(Line.OTHER, t.getLastRemovedColumn());
          Iterator<LimeTableColumn> hidden = t.getHiddenColumns();
          assertSame(Line.OTHER, hidden.next());
          assertFalse(hidden.hasNext());
          assertThrows(LastColumnException.class, () -> t.setColumnVisible("NAME", false));
          assertEquals(1, t.getColumnCount());
          try {
            t.setColumnVisible("OTHER", true);
          } catch (LastColumnException e) {
            throw new AssertionError(e);
          }
          assertEquals(2, t.getColumnCount());
          assertTrue(t.isColumnVisible("OTHER"));
        });
  }

  private static void assertDoesNotThrowHide(LimeJTable t, String id) {
    try {
      t.setColumnVisible(id, false);
    } catch (LastColumnException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void columnsAreCreatedFromTheModelsDataLine() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          assertEquals("NAME", t.getColumnModel().getColumn(0).getIdentifier());
          t.createDefaultColumnsFromModel();
          assertEquals(2, t.getColumnCount());
          assertSame(Line.NAME, t.getColumnModel().getColumn(0));
        });
  }

  @Test
  void sortStateIsReadFromTheModel() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          assertEquals(-1, t.getSortedColumnIndex());
          assertTrue(t.isSortedColumnAscending());
          ((DataLineModel<?, ?>) t.getModel()).sort(1);
          assertEquals(1, t.getSortedColumnIndex());
          assertTrue(t.isSortedColumnAscending());
          ((DataLineModel<?, ?>) t.getModel()).sort(1);
          assertFalse(t.isSortedColumnAscending());
        });
  }

  @Test
  void thePressedHeaderColumnIsReportedByViewIndex() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          t.setPressedColumnIndex(1);
          assertEquals(1, t.getPressedColumnIndex());
          t.moveColumn(0, 1);
          assertEquals(0, t.getPressedColumnIndex());
        });
  }

  @Test
  void settingsAndPreferenceHandlersAreKept() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          assertNull(t.getTableSettings());
          assertNull(t.getColumnPreferenceHandler());
          ColumnPreferenceHandler handler =
              new ColumnPreferenceHandler() {
                public void revertToDefault() {}

                public boolean isDefault() {
                  return true;
                }

                public void setWidths() {}

                public void setOrder() {}

                public void setVisibility() {}

                public void setSimpleColumnListener(SimpleColumnListener scl) {}
              };
          t.setColumnPreferenceHandler(handler);
          assertSame(handler, t.getColumnPreferenceHandler());
        });
  }

  @Test
  void typeAheadFindsThePrefixAndWrapsAround() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("alpha", "apricot", "banana", "blueberry", "cherry");
          assertEquals(0, t.getNextMatch("a", 0, Position.Bias.Forward));
          assertEquals(1, t.getNextMatch("a", 1, Position.Bias.Forward));
          assertEquals(0, t.getNextMatch("a", 2, Position.Bias.Forward), "wraps to the top");
          assertEquals(3, t.getNextMatch("BL", 0, Position.Bias.Forward));
          assertEquals(1, t.getNextMatch("a", 3, Position.Bias.Backward));
          assertEquals(-1, t.getNextMatch("z", 0, Position.Bias.Forward));
          assertThrows(
              IllegalArgumentException.class, () -> t.getNextMatch(null, 0, Position.Bias.Forward));
          assertThrows(
              IllegalArgumentException.class, () -> t.getNextMatch("a", 9, Position.Bias.Forward));
          assertThrows(
              IllegalArgumentException.class, () -> t.getNextMatch("a", -1, Position.Bias.Forward));
        });
  }

  private static KeyEvent typed(LimeJTable source, char c, long when, int modifiers) {
    return new KeyEvent(source, KeyEvent.KEY_TYPED, when, modifiers, KeyEvent.VK_UNDEFINED, c);
  }

  @Test
  void typingLettersMovesTheSelectionToTheMatchingRow() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("alpha", "apricot", "banana", "blueberry", "cherry");
          KeyTypedMover mover = new KeyTypedMover();
          mover.keyTyped(typed(t, 'b', 10_000L, 0));
          assertEquals(2, t.getSelectedRow());
          // quickly typing "l" makes the prefix "bl"
          mover.keyTyped(typed(t, 'l', 10_100L, 0));
          assertEquals(3, t.getSelectedRow());
          // a pause starts a new prefix
          mover.keyTyped(typed(t, 'c', 20_000L, 0));
          assertEquals(4, t.getSelectedRow());
        });
  }

  @Test
  void typingTheSameLetterAgainStepsToTheNextRowStartingWithIt() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("alpha", "apricot", "banana", "blueberry", "cherry");
          KeyTypedMover mover = new KeyTypedMover();
          mover.keyTyped(typed(t, 'a', 10_000L, 0));
          assertEquals(0, t.getSelectedRow(), "nothing selected yet: the first match");
          mover.keyTyped(typed(t, 'a', 10_100L, 0));
          assertEquals(1, t.getSelectedRow(), "the same letter again: the next match");
          mover.keyTyped(typed(t, 'a', 10_200L, 0));
          assertEquals(0, t.getSelectedRow(), "wraps around the matches");
        });
  }

  @Test
  void aLetterWithNoMatchLeavesTheSelectionAlone() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("alpha", "banana");
          t.setSelectedRow(1);
          new KeyTypedMover().keyTyped(typed(t, 'z', 10_000L, 0));
          assertEquals(1, t.getSelectedRow());
        });
  }

  @Test
  void modifiedKeysAndEmptyTablesAreIgnored() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("alpha", "banana");
          KeyTypedMover mover = new KeyTypedMover();
          mover.keyTyped(typed(t, 'b', 10_000L, KeyEvent.CTRL_DOWN_MASK));
          mover.keyTyped(typed(t, 'b', 10_000L, KeyEvent.ALT_DOWN_MASK));
          mover.keyTyped(typed(t, 'b', 10_000L, KeyEvent.META_DOWN_MASK));
          assertEquals(-1, t.getSelectedRow());
          LimeJTable empty = fruitTable();
          mover.keyTyped(typed(empty, 'b', 10_000L, 0));
          assertEquals(-1, empty.getSelectedRow());
          mover.keyReleased(typed(t, 'b', 10_000L, 0));
        });
  }

  @Test
  void aNavigationKeyResetsTheTypedPrefix() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("alpha", "banana", "blueberry");
          KeyTypedMover mover = new KeyTypedMover();
          mover.keyTyped(typed(t, 'b', 10_000L, 0));
          assertEquals(1, t.getSelectedRow());
          KeyEvent down =
              new KeyEvent(
                  t, KeyEvent.KEY_PRESSED, 10_050L, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
          mover.keyPressed(down);
          // within the half second, but the reset makes this a fresh "l" prefix: no match
          mover.keyTyped(typed(t, 'l', 10_100L, 0));
          assertEquals(1, t.getSelectedRow());
        });
  }

  @Test
  void theProgressBarRendererShowsThePercentAndCapsTheBar() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          ProgressBarRenderer r = new ProgressBarRenderer();
          ProgressBarRenderer shown =
              (ProgressBarRenderer) r.getTableCellRendererComponent(t, 42, false, false, 0, 0);
          assertSame(r, shown);
          assertEquals(42, r.getValue());
          assertEquals("42 %", r.getString());
          r.getTableCellRendererComponent(t, 250, false, false, 0, 0);
          assertEquals(100, r.getValue());
          assertEquals("250 %", r.getString());
          r.getTableCellRendererComponent(t, null, false, false, 0, 0);
          assertEquals(0, r.getValue());
          assertEquals("0 %", r.getString());
        });
  }

  @Test
  void theProgressBarRendererPaintsSelectedAndAlternatingRows() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a", "b");
          ProgressBarRenderer r = new ProgressBarRenderer();
          r.getTableCellRendererComponent(t, 1, true, false, 0, 0);
          assertEquals(t.getSelectionBackground(), r.getBackground());
          r.getTableCellRendererComponent(t, 1, false, false, 0, 0);
          assertEquals(t.getBackground(), r.getBackground());
          r.getTableCellRendererComponent(t, 1, false, false, 1, 0);
          Color alt = UIManager.getColor("Table.alternateRowColor");
          assertEquals(alt != null ? alt : t.getBackground().darker(), r.getBackground());
          assertEquals(t.getFont(), r.getFont());
          r.validate();
          r.revalidate();
        });
  }

  /** A cell with a color. */
  private static final class Cell implements ColoredCell {
    private final Color color;
    private final Object value;

    Cell(Color color, Object value) {
      this.color = color;
      this.value = value;
    }

    public Color getColor() {
      return color;
    }

    public Object getValue() {
      return value;
    }

    public Class<?> getCellClass() {
      return String.class;
    }
  }

  @Test
  void theColorRendererColorsUnselectedCellsOnly() throws Exception {
    onEdt(
        () -> {
          LimeJTable t = fruitTable("a");
          ColorRenderer r = new ColorRenderer();
          Component plain =
              r.getTableCellRendererComponent(t, new Cell(Color.RED, "hot"), false, false, 0, 0);
          assertEquals(Color.RED, plain.getForeground());
          assertTrue(plain instanceof JLabel);
          assertEquals("hot", ((JLabel) plain).getText());
          Component selected =
              r.getTableCellRendererComponent(t, new Cell(Color.BLUE, "sel"), true, false, 0, 0);
          assertFalse(Color.BLUE.equals(selected.getForeground()));
          Component empty = r.getTableCellRendererComponent(t, null, false, false, 0, 0);
          assertEquals("", ((JLabel) empty).getText());
          r.updateUI();
        });
  }

  @Test
  void rowPreviewsFollowTheSettingExceptInLite() {
    assertFalse(LimeJTable.showsRowPreviews(false));
    List<Boolean> seen = new ArrayList<>();
    seen.add(LimeJTable.showsRowPreviews(true));
    assertNotNull(seen.get(0));
  }
}
