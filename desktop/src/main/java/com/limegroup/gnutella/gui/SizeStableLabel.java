/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import javax.swing.Icon;
import javax.swing.JLabel;

/**
 * A label for frequently updated text that only asks its container for a new layout when the update
 * changes its size. See {@link SizeStableRevalidation}.
 */
public class SizeStableLabel extends JLabel {
  /** Null while JLabel's constructor runs. */
  private final SizeStableRevalidation revalidation = new SizeStableRevalidation();

  /** An empty, leading-aligned label, like {@code new JLabel()}. */
  public SizeStableLabel() {
    super();
  }

  /** A centered label showing {@code icon}, like {@code new JLabel(icon)}. */
  public SizeStableLabel(Icon icon) {
    super(icon);
  }

  @Override
  public void setText(String text) {
    if (revalidation == null) {
      super.setText(text);
    } else {
      revalidation.setText(() -> super.setText(text));
    }
  }

  @Override
  public void revalidate() {
    if (revalidation == null || revalidation.allowsRevalidate(this)) {
      super.revalidate();
    }
  }
}
