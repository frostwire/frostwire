package com.limegroup.gnutella.gui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Defines the "border" line that is used to separate the "My Extensions" item in a CheckBoxList.
 */
class SeperatorBorder implements Border {
    public SeperatorBorder() {
    }

    public Insets getBorderInsets(Component c) {
        return new Insets(8, 4, 2, 4);
    }

    public boolean isBorderOpaque() {
        return false;
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(UIManager.getColor("List.foreground").brighter().brighter());
        g.fillRect(x + 4, y, width - 12, 2);
        g.setColor(UIManager.getColor("List.foreground").darker());
        g.fillRect(x + 4, y, width - 12, 1);
    }
}
