/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;

/**
 * A PopupFactory that keeps tooltips inside their window. Swing makes a tooltip that does not fit
 * inside the window a medium-weight popup, whose JLayeredPane.add creates a native Panel peer on
 * the EDT (WCanvasPeer.create, Icebase #2bb3702e758c, frostwire/frostwire#1552). Moved to fit, it
 * stays a lightweight component. Other popups (menus, the search suggestions) are untouched.
 */
public final class WindowBoundTooltips extends PopupFactory {

  /** Installs it as the shared factory. Call it once, on startup. */
  public static void install() {
    PopupFactory.setSharedInstance(new WindowBoundTooltips());
  }

  @Override
  public Popup getPopup(Component owner, Component contents, int x, int y)
      throws IllegalArgumentException {
    if (contents instanceof JToolTip && owner != null) {
      Window window = SwingUtilities.getWindowAncestor(owner);
      if (window != null && window.isShowing()) {
        Point p = clamp(x, y, contents.getPreferredSize(), window.getBounds());
        x = p.x;
        y = p.y;
      }
    }
    return super.getPopup(owner, contents, x, y);
  }

  /**
   * The position closest to (x, y) at which a popup of {@code size} is inside {@code bounds} (the
   * top left corner if it is larger than the bounds). Screen coordinates.
   */
  static Point clamp(int x, int y, Dimension size, Rectangle bounds) {
    int maxX = bounds.x + bounds.width - size.width;
    int maxY = bounds.y + bounds.height - size.height;
    int cx = Math.max(bounds.x, Math.min(x, maxX));
    int cy = Math.max(bounds.y, Math.min(y, maxY));
    return new Point(cx, cy);
  }
}
