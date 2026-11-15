/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

/**
 * The tooltip timer asked for the cell renderer of the column under the mouse, but columnAtPoint is
 * -1 in the empty area right of the last column, and getCellRenderer(row, -1) threw
 * ArrayIndexOutOfBoundsException on the EDT (Icebase #ae0ca4ff1c4e, Windows 11, build 337).
 *
 * <p>LimeJTable's constructor reads the theme's table font, which needs a display, so the table is
 * built through JTable's constructor only.
 */
class LimeJTableTooltipOutsideCellsTest {

  @Test
  void noTooltipRightOfTheLastColumnOrBelowTheLastRow() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          LimeJTableHiddenColumnTooltipTest.Model model =
              new LimeJTableHiddenColumnTooltipTest.Model();
          model.add("row", 0);
          LimeJTable table = headlessTable(model);
          table.setSize(1000, 500);
          int y = table.getRowHeight() / 2;
          int rightOfLastColumn = table.getColumnModel().getTotalColumnWidth() + 100;
          assertEquals(-1, table.columnAtPoint(new Point(rightOfLastColumn, y)));

          assertNull(table.getToolTipText(mouseAt(table, rightOfLastColumn, y)));
          assertNull(table.getToolTipText(mouseAt(table, 5, table.getRowHeight() * 10)));
          assertNull(table.getToolTipText(mouseAt(table, rightOfLastColumn, 400)));
        });
  }

  private static LimeJTable headlessTable(TableModel model) {
    try {
      Constructor<?> constructor =
          ReflectionFactory.getReflectionFactory()
              .newConstructorForSerialization(
                  LimeJTable.class, JTable.class.getDeclaredConstructor(TableModel.class));
      return (LimeJTable) constructor.newInstance(model);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static MouseEvent mouseAt(LimeJTable table, int x, int y) {
    return new MouseEvent(
        table, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false);
  }
}
