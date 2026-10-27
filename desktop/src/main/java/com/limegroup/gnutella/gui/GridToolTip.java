/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;

/**
 * A tooltip that lays its text out as a grid of plain labels, for label/value tooltips that would
 * otherwise be written as an HTML {@code <table>}.
 *
 * <p>Swing renders an HTML tooltip by parsing it and building a CSS view tree on the EDT every time
 * the tip is shown, and table layouts are the most expensive part of that (Icebase #a9acc27ec32d).
 * This tooltip never goes through {@code BasicHTML}: the tip text is plain, with {@link #ROW}
 * between rows and {@link #CELL} between cells (see {@link #text}), and each cell is a {@link
 * JLabel} with HTML rendering disabled.
 *
 * <p>Use it by overriding {@code createToolTip()} in the component whose {@code getToolTipText()}
 * returns such text.
 */
public class GridToolTip extends JToolTip {
  public static final String ROW = "\n";
  public static final String CELL = "\t";

  private static final String HTML_DISABLE = "html.disable";
  // Spacing of a default HTML table (cellspacing 2, cellpadding 1), so the grid looks the same
  private static final int EDGE_PADDING = 6;
  private static final int CELL_PADDING = 3;
  private static final int CELL_GAP = 8;

  private final int[] rightAlignedColumns;
  private boolean paintingBackground;

  /**
   * @param rightAlignedColumns zero-based indexes of the columns to align right, e.g. numbers
   */
  public GridToolTip(int... rightAlignedColumns) {
    this.rightAlignedColumns = rightAlignedColumns.clone();
    putClientProperty(HTML_DISABLE, Boolean.TRUE);
    setLayout(new GridBagLayout());
  }

  /** Joins rows of cells into tip text for this tooltip. */
  public static String text(String[]... rows) {
    return Arrays.stream(rows)
        .map(cells -> String.join(CELL, cells))
        .collect(Collectors.joining(ROW));
  }

  @Override
  public void setTipText(String tipText) {
    super.setTipText(tipText);
    removeAll();
    if (tipText == null) {
      return;
    }
    String[] rows = tipText.split(ROW, -1);
    GridBagConstraints c = new GridBagConstraints();
    for (c.gridy = 0; c.gridy < rows.length; c.gridy++) {
      String[] cells = rows[c.gridy].split(CELL, -1);
      for (c.gridx = 0; c.gridx < cells.length; c.gridx++) {
        boolean right = isRightAligned(c.gridx);
        c.anchor = right ? GridBagConstraints.LINE_END : GridBagConstraints.LINE_START;
        c.insets =
            new Insets(
                c.gridy == 0 ? EDGE_PADDING : CELL_PADDING,
                c.gridx == 0 ? EDGE_PADDING : 0,
                c.gridy == rows.length - 1 ? EDGE_PADDING : CELL_PADDING,
                c.gridx == cells.length - 1 ? EDGE_PADDING : CELL_GAP);
        add(createCell(cells[c.gridx], right), c);
      }
    }
    revalidate();
  }

  private JLabel createCell(String text, boolean rightAligned) {
    JLabel cell = new JLabel(text, rightAligned ? SwingConstants.RIGHT : SwingConstants.LEADING);
    cell.putClientProperty(HTML_DISABLE, Boolean.TRUE);
    cell.setFont(getFont());
    cell.setForeground(getForeground());
    cell.setOpaque(false);
    return cell;
  }

  private boolean isRightAligned(int column) {
    for (int c : rightAlignedColumns) {
      if (c == column) {
        return true;
      }
    }
    return false;
  }

  /** The grid's size, not the look and feel's single-line measure of the raw tip text. */
  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    return getLayout().preferredLayoutSize(this);
  }

  /** Lets the look and feel paint the background and border; the cells paint the text. */
  @Override
  protected void paintComponent(Graphics g) {
    paintingBackground = true;
    try {
      super.paintComponent(g);
    } finally {
      paintingBackground = false;
    }
  }

  @Override
  public String getTipText() {
    return paintingBackground ? "" : super.getTipText();
  }
}
