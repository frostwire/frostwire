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

import javax.swing.*;
import java.awt.*;

/**
 * Draws icons that are ascending or descending.
 */
public class SortArrowIcon implements Icon {
    public static final int NONE = 0;
    static final int DESCENDING = 1;
    static final int ASCENDING = 2;
    private static final Icon DESCENDING_ICON = new SortArrowIcon(DESCENDING);
    private static final Icon ASCENDING_ICON = new SortArrowIcon(ASCENDING);
    protected final int width = 8;
    protected final int height = 8;
    final int direction;

    SortArrowIcon(int direction) {
        this.direction = direction;
    }

    static Icon getAscendingIcon() {
        return ASCENDING_ICON;
    }

    static Icon getDescendingIcon() {
        return DESCENDING_ICON;
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
        Color light;
        Color shade;
        if (bg.getRed() >= 0xFC && bg.getGreen() >= 0xFC && bg.getBlue() >= 0xFC) {
            light = bg.darker();
            shade = light.darker();
        } else if (bg.getRed() <= 0x03 && bg.getGreen() <= 0x03 && bg.getBlue() <= 0x03) {
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

