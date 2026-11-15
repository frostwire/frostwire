/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import java.awt.event.MouseEvent;
import java.lang.reflect.Constructor;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sun.reflect.ReflectionFactory;

/** Lite shows no row preview tooltips; Full keeps them (frostwire/frostwire#1555). */
class LimeJTablePreviewTooltipTest {
  /** Whether the rows say their tooltip is required (like the karma column's). */
  private static volatile boolean required;

  /** Settings keys are unique per JVM, so every table here shares one (tooltips on). */
  private static final TableSettings SETTINGS = new TableSettings("PREVIEW_TOOLTIP_TEST");

  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
    required = false;
  }

  @Test
  void fullShowsTheRowPreview() throws Exception {
    assertTrue(showsATooltipOverACell());
  }

  @Test
  void liteShowsNoRowPreview() throws Exception {
    PerformanceMode.resolve(Choice.LITE, null);
    assertFalse(showsATooltipOverACell());
  }

  @Test
  void liteStillShowsTooltipsARowRequires() throws Exception {
    PerformanceMode.resolve(Choice.LITE, null);
    required = true;
    assertTrue(showsATooltipOverACell());
  }

  /** Whether the table asks the tooltip manager for a tip over a short, unclipped cell. */
  private static boolean showsATooltipOverACell() throws Exception {
    boolean[] shown = new boolean[1];
    SwingUtilities.invokeAndWait(
        () -> {
          PreviewModel model = new PreviewModel();
          model.add("row", 0);
          LimeJTable table = headlessTable(model);
          table.setTableSettings(SETTINGS);
          table.setSize(1000, 500);
          shown[0] =
              table.getToolTipText(
                      new MouseEvent(
                          table,
                          MouseEvent.MOUSE_MOVED,
                          System.currentTimeMillis(),
                          0,
                          5,
                          table.getRowHeight() / 2,
                          0,
                          false))
                  != null;
        });
    return shown[0];
  }

  static final class PreviewLine extends AbstractDataLine<String> {
    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return new LimeTableColumn(col, "COL" + col, "col" + col, 500, true, String.class);
    }

    @Override
    public boolean isDynamic(int col) {
      return false;
    }

    @Override
    public boolean isClippable(int col) {
      return false;
    }

    @Override
    public Object getValueAt(int col) {
      return "x";
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public String[] getToolTipArray(int col) {
      return new String[] {"Status: Downloading", "Progress: 40%"};
    }

    @Override
    public boolean isTooltipRequired(int col) {
      return required;
    }

    @Override
    public void update() {}
  }

  static final class PreviewModel extends BasicDataLineModel<PreviewLine, String> {
    PreviewModel() {
      super(PreviewLine.class);
    }
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
}
