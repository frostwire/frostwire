/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.tables;

import com.frostwire.gui.theme.SkinProgressBarUI;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * This class handles rendering a <tt>JProgressBar</tt> for improved
 * performance in tables.
 *
 * @author gubatron
 * @author aldenml
 */
class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {
    private boolean isSelected;

    /**
     * Sets the font, border, and colors for the progress bar.
     */
    ProgressBarRenderer() {
        setUI(SkinProgressBarUI.createUI(this));
        setStringPainted(true);
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSel, boolean hasFocus, int row, int column) {
        this.isSelected = isSel;
        setValue(Math.min(100, getBarStatus(value)));
        setString(getDescription(value));
        syncFont(table, this);
        return this;
    }

    /**
     * @param value the same value that initializes the cell
     * @return the String that should be displayed
     */
    private String getDescription(Object value) {
        return getBarStatus(value) + " %";
    }

    /**
     * @param value the same value that initializes the cell
     * @return what the progress bar component should be set to
     */
    private int getBarStatus(Object value) {
        return value == null ? 0 : (Integer) value;
    }

    @Override
    protected void paintBorder(Graphics g) {
        super.paintBorder(g);
        if (!isSelected) {
            BeveledCellPainter.paintBorder(g, getWidth(), getHeight());
        }
    }

    /*
     * The following methods are overridden as a performance measure to
     * to prune code-paths are often called in the case of renders
     * but which we know are unnecessary.  Great care should be taken
     * when writing your own renderer to weigh the benefits and
     * drawbacks of overriding methods like these.
     */

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public boolean isOpaque() {
        Color back = getBackground();
        Component p = getParent();
        if (p != null) {
            p = p.getParent();
        }
        JComponent jp = (JComponent) p;
        // p should now be the JTable. 
        boolean colorMatch = (back != null) && (p != null) && back.equals(p.getBackground()) && jp.isOpaque();
        return !colorMatch && super.isOpaque();
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void validate() {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void revalidate() {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void repaint(long tm, int x, int y, int width, int height) {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    public void repaint(Rectangle r) {
    }

    /**
     * Overridden for performance reasons.
     * See the <a href="#override">Implementation Note</a>
     * for more information.
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        // Strings get interned...
        if (propertyName.equals("text")) {
            super.firePropertyChange(propertyName, oldValue, newValue);
        }
    }

    private void syncFont(JTable table, JComponent c) {
        Font tableFont = table.getFont();
        if (tableFont != null && !tableFont.equals(c.getFont())) {
            c.setFont(tableFont);
        }
    }
}
