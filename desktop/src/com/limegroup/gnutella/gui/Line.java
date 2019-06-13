package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;

/**
 * A component that draws a line.
 */
public class Line extends JComponent {
    private Color color;
    private Color uiColor;

    /**
     * Creates a line that uses a color from the current theme.
     */
    public Line() {
        uiColor = UIManager.getColor("controlShadow");
        initSize();
    }

    private void initSize() {
        setPreferredSize(new Dimension(1, 1));
        setMaximumSize(new Dimension(Short.MAX_VALUE, 1));
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Color oldColor = g.getColor();
        if (uiColor != null) {
            g.setColor(uiColor);
        } else if (color != null) {
            g.setColor(color);
        } // fall back to default foreground color
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(oldColor);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (uiColor != null) {
            uiColor = UIManager.getColor("controlShadow");
        }
    }
}