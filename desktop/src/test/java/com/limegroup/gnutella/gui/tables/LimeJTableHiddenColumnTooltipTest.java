/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JTable;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import org.junit.jupiter.api.Test;

/**
 * The tooltip of a clipped cell measured its data with a renderer, passing it the column's MODEL
 * index as if it were a VIEW index. With columns hidden, the model index is past the last view
 * column, and the renderer's getColumnClass threw ArrayIndexOutOfBoundsException on the EDT's
 * tooltip timer (Icebase #ea44101740a1, Windows 11, build 337, 5 reports).
 */
class LimeJTableHiddenColumnTooltipTest {

  static final class Line extends AbstractDataLine<String> {
    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return new LimeTableColumn(col, "COL" + col, "col" + col, 50, true, String.class);
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
      return "a value of column " + col;
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public void update() {}
  }

  static final class Model extends BasicDataLineModel<Line, String> {
    Model() {
      super(Line.class);
    }
  }

  @Test
  void theDataWidthOfAColumnAfterHiddenOnesDoesNotThrow() throws Exception {
    LookAndFeel previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new NimbusLookAndFeel());
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            Model model = new Model();
            model.add("row", 0);
            JTable table = new JTable(model);
            // hide the first two columns: model column 2 is now view column 0
            table.removeColumn(table.getColumnModel().getColumn(0));
            table.removeColumn(table.getColumnModel().getColumn(0));

            int[] width = new int[1];
            assertDoesNotThrow(
                () -> width[0] = LimeJTable.dataWidth(table, model, 0, 2, new String[1]));

            assertTrue(width[0] > 0, "the visible column has a width: " + width[0]);
            assertEquals(
                -1,
                LimeJTable.dataWidth(table, model, 0, 0, new String[1]),
                "a hidden column has none");
          });
    } finally {
      UIManager.setLookAndFeel(previous);
    }
  }
}
