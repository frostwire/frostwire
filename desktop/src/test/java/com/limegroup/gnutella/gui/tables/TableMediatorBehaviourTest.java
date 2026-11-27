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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.PaddedPanel;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The shared table mediator behind every FrostWire table: adding, removing and selecting rows,
 * header clicks and sorting, and the column menu and preferences. Settings go to the test JVM's own
 * home folder.
 */
class TableMediatorBehaviourTest {

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

  public static final class FruitLine extends AbstractDataLine<String> {
    /** Column ids are stored in the shared table settings, so every mediator gets its own. */
    static volatile String prefix = "MEDIATOR_TEST";

    final LimeTableColumn NAME =
        new LimeTableColumn(0, prefix + "_NAME", "Name", 120, true, String.class);
    final LimeTableColumn LENGTH =
        new LimeTableColumn(1, prefix + "_LENGTH", "Length", 60, true, Integer.class);
    final LimeTableColumn HIDDEN =
        new LimeTableColumn(2, prefix + "_HIDDEN", "Hidden", 40, false, String.class);

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return col == 0 ? NAME : col == 1 ? LENGTH : HIDDEN;
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
      return col == 0 ? initializer : col == 1 ? (Object) initializer.length() : "h";
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }
  }

  static final class FruitModel extends HashBasedDataLineModel<FruitLine, String> {
    FruitModel() {
      super(FruitLine.class);
    }
  }

  static final class FruitMediator extends AbstractTableMediator<FruitModel, FruitLine, String> {
    // the base constructor calls handleNoSelection() before this class's fields exist
    List<String> events;
    int actionKeys;

    String id() {
      return ID;
    }

    FruitMediator(String id) {
      super(id);
      events = new ArrayList<>();
    }

    @Override
    protected void updateSplashScreen() {}

    @Override
    protected void setupConstants() {
      MAIN_PANEL = new PaddedPanel(0);
      DATA_MODEL = new FruitModel();
      TABLE = new LimeJTable(DATA_MODEL);
    }

    @Override
    protected JPopupMenu createPopupMenu() {
      JPopupMenu menu = new JPopupMenu();
      menu.add(new JMenuItem("only item"));
      return menu;
    }

    @Override
    public void handleActionKey() {
      actionKeys++;
    }

    @Override
    public void handleSelection(int row) {
      if (events != null) events.add("selected " + row);
    }

    @Override
    public void handleNoSelection() {
      if (events != null) events.add("none");
    }

    LimeJTable table() {
      return TABLE;
    }

    FruitModel model() {
      return DATA_MODEL;
    }

    void triggerResort() {
      resortNow();
    }

    void leftClickHeader(int x) {
      TABLE.getTableHeader().setSize(TABLE.getColumnModel().getTotalColumnWidth(), 20);
      handleHeaderColumnLeftClick(new Point(x, 5));
    }
  }

  private static FruitMediator mediator(String id, String... fruit) throws Exception {
    FruitMediator[] holder = new FruitMediator[1];
    SwingUtilities.invokeAndWait(
        () -> {
          // the test home keeps the table settings between runs: a fresh id each time
          String unique = id + "_" + Long.toHexString(System.nanoTime());
          FruitLine.prefix = unique;
          holder[0] = new FruitMediator(unique);
          for (String f : fruit) {
            holder[0].add(f);
          }
          holder[0].events.clear();
        });
    return holder[0];
  }

  private static String id(FruitMediator m, String column) {
    return m.id() + "_" + column;
  }

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }

  @Test
  void aNewMediatorHasItsPanelTableAndDefaultRenderers() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_BUILD");
    onEdt(
        () -> {
          assertNotNull(m.getComponent());
          assertEquals(0, m.getSize());
          assertEquals(2, m.table().getColumnCount(), "the third column is hidden by default");
          assertFalse(m.table().isColumnVisible(id(m, "HIDDEN")));
        });
  }

  @Test
  void addedRowsGoToTheEndOrTheRequestedPlace() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_ADD");
    onEdt(
        () -> {
          // add(item) without a place puts the row first
          m.add("fig");
          m.add("apple");
          assertEquals("apple", m.model().get(0).getInitializeObject());
          m.add("kiwi", 1);
          assertEquals(3, m.getSize());
          assertEquals("apple", m.model().get(0).getInitializeObject());
          assertEquals("kiwi", m.model().get(1).getInitializeObject());
          assertEquals("fig", m.model().get(2).getInitializeObject());
          m.add("plum", 99);
          assertEquals("plum", m.model().get(0).getInitializeObject());
          m.addUnsorted("lime");
          assertEquals("lime", m.model().get(0).getInitializeObject());
        });
  }

  @Test
  void aSortedTableInsertsNewRowsInOrder() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_SORTED", "pear", "apple");
    onEdt(
        () -> {
          m.model().sort(0);
          m.model().doResort();
          m.add("fig");
          m.add("zucchini");
          List<String> order = new ArrayList<>();
          for (int i = 0; i < m.getSize(); i++) {
            order.add(m.model().get(i).getInitializeObject());
          }
          assertEquals(List.of("apple", "fig", "pear", "zucchini"), order);
        });
  }

  @Test
  void removeUpdateAndClearWorkOnTheModel() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_REMOVE", "a", "bb", "ccc");
    onEdt(
        () -> {
          m.remove("bb");
          m.remove("not there");
          assertEquals(2, m.getSize());
          m.update("ccc");
          m.clearTable();
          assertEquals(0, m.getSize());
          assertTrue(m.events.contains("none"));
        });
  }

  @Test
  void selectingARowTellsTheMediatorAndTheSelectionCanBeRemoved() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_SELECT", "a", "bb", "ccc");
    onEdt(
        () -> {
          m.setSelectedRow(1);
          assertEquals(1, m.table().getSelectedRow());
          assertTrue(m.events.contains("selected 1"), m.events.toString());
          m.selectRow(2);
          m.selectRow(99);
          assertEquals(2, m.table().getSelectedRows().length);
          m.removeSelection();
          assertEquals(1, m.getSize());
          assertEquals("ccc", m.model().get(0).getInitializeObject());
          assertEquals(-1, m.table().getSelectedRow());
        });
  }

  @Test
  void enterRunsTheActionKeyAndDeleteRemovesTheSelection() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_KEYS", "a", "bb");
    onEdt(
        () -> {
          m.table().setSelectedRow(0);
          Object enter =
              m.table()
                  .getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                  .get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
          m.table().getActionMap().get(enter).actionPerformed(null);
          assertEquals(1, m.actionKeys);
          Object delete =
              m.table()
                  .getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                  .get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
          m.table().getActionMap().get(delete).actionPerformed(null);
          assertEquals(1, m.getSize());
          m.handleMouseDoubleClick();
          assertEquals(2, m.actionKeys);
        });
  }

  @Test
  void aRightClickSelectsTheRowUnderThePointerUnlessItIsSelected() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_RIGHTCLICK", "a", "bb", "ccc");
    onEdt(
        () -> {
          m.table().setSize(300, 200);
          int rowHeight = m.table().getRowHeight();
          Point secondRow = new Point(10, rowHeight + rowHeight / 2);
          MouseEvent click =
              new MouseEvent(
                  m.table(),
                  MouseEvent.MOUSE_PRESSED,
                  1L,
                  0,
                  secondRow.x,
                  secondRow.y,
                  1,
                  true,
                  MouseEvent.BUTTON3);
          m.handleRightMouseClick(click);
          assertEquals(1, m.table().getSelectedRow());
          // already selected: the selection stays as it is
          m.table().addRowSelectionInterval(2, 2);
          m.handleRightMouseClick(click);
          assertEquals(2, m.table().getSelectedRows().length);
          // below the last row: nothing happens
          MouseEvent below =
              new MouseEvent(
                  m.table(), MouseEvent.MOUSE_PRESSED, 1L, 0, 10, 190, 1, true, MouseEvent.BUTTON3);
          m.handleRightMouseClick(below);
          assertEquals(2, m.table().getSelectedRows().length);
        });
  }

  @Test
  void clickingAHeaderSortsByThatColumnAndClickingAgainReversesIt() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_HEADER", "pear", "fig");
    onEdt(
        () -> {
          m.leftClickHeader(10);
          assertEquals(0, m.model().getSortColumn());
          assertTrue(m.model().isSortAscending());
          m.leftClickHeader(10);
          assertFalse(m.model().isSortAscending());
          m.leftClickHeader(m.table().getColumnModel().getColumn(0).getWidth() + 10);
          assertEquals(1, m.model().getSortColumn());
          // a click past the last column does nothing
          m.leftClickHeader(5000);
          assertEquals(1, m.model().getSortColumn());
        });
  }

  @Test
  void pressingAndReleasingAHeaderMarksThePressedColumn() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_PRESS", "pear");
    onEdt(
        () -> {
          m.table().getTableHeader().setSize(m.table().getColumnModel().getTotalColumnWidth(), 20);
          m.handleHeaderColumnPressed(new Point(10, 5));
          assertEquals(0, m.table().getPressedColumnIndex());
          m.handleHeaderColumnReleased(new Point(10, 5));
          assertEquals(-1, m.table().getPressedColumnIndex());
          m.handleHeaderColumnPressed(new Point(5000, 5));
          assertEquals(-1, m.table().getPressedColumnIndex());
        });
  }

  @Test
  void theColumnMenuListsEveryColumnAndTogglesVisibility() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_MENU", "pear");
    onEdt(
        () -> {
          ColumnSelectionMenu menu = new ColumnSelectionMenu(m.table());
          JPopupMenu popup = menu.getComponent();
          // revert, more options, separator, three columns
          assertEquals(6, popup.getComponentCount());
          JMenuItem revert = (JMenuItem) popup.getComponent(0);
          assertTrue(popup.getComponent(1) instanceof JMenu);
          JCheckBoxMenuItem length = (JCheckBoxMenuItem) popup.getComponent(4);
          JCheckBoxMenuItem hidden = (JCheckBoxMenuItem) popup.getComponent(5);
          assertTrue(length.getState());
          assertFalse(hidden.getState());
          assertTrue(m.table().isColumnVisible(id(m, "LENGTH")));
          length.doClick();
          assertFalse(m.table().isColumnVisible(id(m, "LENGTH")));
          hidden.doClick();
          assertTrue(m.table().isColumnVisible(id(m, "HIDDEN")));
          assertEquals(2, m.table().getColumnCount());
          assertNotNull(revert);
        });
  }

  @Test
  void theMoreOptionsMenuChangesTheTableSettings() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_OPTIONS", "pear");
    onEdt(
        () -> {
          JPopupMenu popup = new ColumnSelectionMenu(m.table()).getComponent();
          JMenu options = (JMenu) popup.getComponent(1);
          assertEquals(2, options.getItemCount());
          JCheckBoxMenuItem sorting = (JCheckBoxMenuItem) options.getItem(0);
          boolean before = m.table().getTableSettings().REAL_TIME_SORT.getValue();
          assertEquals(before, sorting.getState());
          sorting.doClick();
          assertEquals(!before, m.table().getTableSettings().REAL_TIME_SORT.getValue());
          m.table().getTableSettings().revertToDefault();
          assertTrue(m.table().getTableSettings().isDefault());
        });
  }

  @Test
  void hidingAColumnIsRememberedAndRevertingRestoresTheDefaults() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_PREFS", "pear");
    onEdt(
        () -> {
          ColumnPreferenceHandler prefs = m.table().getColumnPreferenceHandler();
          assertNotNull(prefs);
          assertTrue(prefs.isDefault());
          try {
            m.table().setColumnVisible(id(m, "LENGTH"), false);
          } catch (LastColumnException e) {
            throw new AssertionError(e);
          }
          assertFalse(prefs.isDefault());
          prefs.revertToDefault();
          assertTrue(m.table().isColumnVisible(id(m, "LENGTH")));
          assertTrue(prefs.isDefault());
          assertEquals(2, m.table().getColumnCount());
        });
  }

  @Test
  void aMovedColumnIsNoLongerTheDefaultOrder() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_ORDER", "pear");
    onEdt(
        () -> {
          ColumnPreferenceHandler prefs = m.table().getColumnPreferenceHandler();
          m.table().getColumnModel().moveColumn(0, 1);
          assertFalse(prefs.isDefault());
          prefs.revertToDefault();
          assertEquals(id(m, "NAME"), m.table().getColumnModel().getColumn(0).getIdentifier());
        });
  }

  @Test
  void theExtraPopupMenuOfTheMediatorIsBuiltOnRequest() throws Exception {
    FruitMediator m = mediator("MEDIATOR_TEST_POPUP", "pear");
    onEdt(
        () -> {
          JPopupMenu menu = m.createPopupMenu();
          assertEquals(1, menu.getComponentCount());
          m.setButtonEnabled(0, false);
        });
  }
}
