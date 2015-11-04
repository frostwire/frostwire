/*
=====================================================================

  SortArrowIcon.java
  
  Created by Claude Duguay
  Copyright (c) 2002
  
  Taken freely from:
   http://www.fawcette.com/javapro/2002_08/magazine/columns/visualcomponents/
   at the 'download code' link.
   
  Added package and factory methods for retrieving the icons.
  
=====================================================================
*/

package com.limegroup.gnutella.gui.tables;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

/**
 * Draws icons that are ascending or descending.
 */
public class SortArrowIcon implements Icon {
  public static final int NONE = 0;
  public static final int DESCENDING = 1;
  public static final int ASCENDING = 2;

  protected int direction;
  protected int width = 8;
  protected int height = 8;
  
  private static final Icon DESCENDING_ICON = new SortArrowIcon(DESCENDING);
  private static final Icon ASCENDING_ICON = new SortArrowIcon(ASCENDING);
  private static final Icon NONE_ICON = new SortArrowIcon(NONE);
  
  public static Icon getAscendingIcon() {
    return ASCENDING_ICON;
  }
  
  public static Icon getDescendingIcon() {
    return DESCENDING_ICON;
  }
  
  public static Icon getNoneIcon() {
    return NONE_ICON;
  }
  
  public SortArrowIcon(int direction) {
    this.direction = direction;
  }
  
  public int getIconWidth() {
    return width;
  }
  
  public int getIconHeight() {
    return height;
  }
  
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (direction == NONE)
      return;

    Color bg = c.getBackground();
    // Compute two good contrasting shades of the background colors
    Color light = null;
    Color shade = null;
    if (bg.getRed() >= 0xFC && bg.getGreen() >= 0xFC && bg.getBlue() >= 0xFC) {
      light = bg.darker();
      shade = light.darker();
    } else
    if (bg.getRed() <= 0x03 && bg.getGreen() <= 0x03 && bg.getBlue() <= 0x03) {
      shade = bg.brighter();
      light = shade.brighter();
    } else {
      light = bg.brighter();
      shade = bg.darker();
    }
  
    int w = width;
    int h = height;
    int m = w / 2;

    switch (direction) {
    case DESCENDING:
      g.setColor(shade);
      g.drawLine(x, y, x + w, y);
      g.drawLine(x, y, x + m, y + h);
      g.setColor(light);
      g.drawLine(x + w, y, x + m, y + h);
      break;
    case ASCENDING:
      g.setColor(shade);
      g.drawLine(x + m, y, x, y + h);
      g.setColor(light);
      g.drawLine(x, y + h, x + w, y + h);
      g.drawLine(x + m, y, x + w, y + h);
      break;
    }
  }
}

