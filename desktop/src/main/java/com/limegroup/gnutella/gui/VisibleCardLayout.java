/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.function.Function;

/**
 * A {@link CardLayout} whose minimum and preferred sizes are those of the card on show.
 *
 * <p>CardLayout sizes its container after the largest of all its cards, hidden ones included. In a
 * container that holds whole tabs, every layout pass of the window then measured every hidden tab,
 * and any hidden tab with a pending change (search results arriving behind the Transfers tab, say)
 * was laid out from scratch just to be measured. Only the card on show is drawn, so only its size
 * matters. Cards are still laid out like CardLayout does; showing another card invalidates the
 * container, so the window takes the new card's size on its next layout.
 */
public class VisibleCardLayout extends CardLayout {

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    return sizeOfVisibleCard(parent, Component::getPreferredSize);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return sizeOfVisibleCard(parent, Component::getMinimumSize);
  }

  private Dimension sizeOfVisibleCard(Container parent, Function<Component, Dimension> size) {
    synchronized (parent.getTreeLock()) {
      Dimension card = new Dimension();
      for (Component component : parent.getComponents()) {
        if (component.isVisible()) {
          card = size.apply(component);
          break;
        }
      }
      Insets insets = parent.getInsets();
      return new Dimension(
          insets.left + insets.right + card.width + getHgap() * 2,
          insets.top + insets.bottom + card.height + getVgap() * 2);
    }
  }
}
