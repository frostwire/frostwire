/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.Dimension;
import javax.swing.JComponent;

/**
 * Filters the revalidation a component's own text update asks for, letting it through only when the
 * new text changes a size the layout sees (minimum, preferred or maximum).
 *
 * <p>{@code JLabel.setText} and {@code AbstractButton.setText} revalidate on every text change.
 * Invalidation climbs to the top of the window, so the next validate pass lays out the whole window
 * and ends in a native cursor update (a cross-thread call on Windows and macOS). Text that keeps
 * the same size only needs the repaint {@code setText} already schedules.
 *
 * <p>Only the revalidation coming from {@code setText} is filtered. Every other one (UI install,
 * border, font, icon) passes through untouched: Synth skins revalidate while installing the UI,
 * before the component has a style to measure its sizes with.
 *
 * <p>A component uses it by wrapping {@code super.setText} in {@link #setText} and calling {@code
 * super.revalidate()} only when {@link #allowsRevalidate} says so. JComponent constructors call
 * both before the subclass's fields are set, so callers must treat a null instance as "allow".
 */
public final class SizeStableRevalidation {
  private Dimension laidOutMinimumSize;
  private Dimension laidOutPreferredSize;
  private Dimension laidOutMaximumSize;
  private boolean settingText;

  /** Runs the component's {@code super.setText} so that its revalidation can be filtered. */
  public void setText(Runnable superSetText) {
    settingText = true;
    try {
      superSetText.run();
    } finally {
      settingText = false;
    }
  }

  /** Whether {@code component}'s {@code revalidate()} must go through to {@code super}. */
  public boolean allowsRevalidate(JComponent component) {
    if (!settingText) {
      laidOutPreferredSize = null;
      return true;
    }
    Dimension minimumSize = component.getMinimumSize();
    Dimension preferredSize = component.getPreferredSize();
    Dimension maximumSize = component.getMaximumSize();
    if (preferredSize.equals(laidOutPreferredSize)
        && minimumSize.equals(laidOutMinimumSize)
        && maximumSize.equals(laidOutMaximumSize)) {
      return false;
    }
    laidOutMinimumSize = minimumSize;
    laidOutPreferredSize = preferredSize;
    laidOutMaximumSize = maximumSize;
    return true;
  }
}
