/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class FWAbstractJPanelTableCellRendererTest {

  private static final class LabelRenderer extends FWAbstractJPanelTableCellRenderer {
    final JLabel label = new JLabel();

    LabelRenderer() {
      setLayout(new BorderLayout());
      add(label, BorderLayout.CENTER);
    }

    @Override
    protected void updateUIData(Object dataHolder, JTable table, int row, int column) {
      label.setText(String.valueOf(dataHolder));
    }
  }

  @Test
  void validateLaysOutChildrenOfAnInvalidatedRenderer() {
    LabelRenderer renderer = new LabelRenderer();
    renderer.setSize(120, 20);
    renderer.label.setText("a much longer cell value"); // invalidates the renderer
    assertFalse(renderer.isValid());

    // The override must still lay out the children (only the cursor update is skipped);
    // otherwise cells would paint with zero-sized children.
    renderer.validate();

    assertEquals(new Rectangle(0, 0, 120, 20), renderer.label.getBounds());
  }
}
