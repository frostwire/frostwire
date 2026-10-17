/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Dimension;
import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * A label for frequently updated text that only asks its container for a new layout when the update
 * changes its preferred size.
 *
 * <p>{@code JLabel.setText} revalidates on every text change, and every validate pass of the window
 * ends in a native cursor update (a cross-thread call on Windows and macOS). Text that keeps the
 * same size only needs the repaint {@code setText} already schedules.
 */
class SizeStableLabel extends JLabel {
  private Dimension laidOutSize;

  SizeStableLabel(Icon icon) {
    super(icon);
  }

  @Override
  public void revalidate() {
    Dimension size = getPreferredSize();
    if (!size.equals(laidOutSize)) {
      laidOutSize = size;
      super.revalidate();
    }
  }
}
