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
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * This class handles rendering a <tt>JProgressBar</tt> for improved
 * performance in tables.
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public class ProgressBarRenderer extends JProgressBar implements TableCellRenderer {

    private Map<Color, Border> borders = new HashMap<Color, Border>();
    private boolean isSelected;

    /**
     * Sets the font, border, and colors for the progress bar.
     *
     */
    public ProgressBarRenderer() {
        setUI(SkinProgressBarUI.createUI(this));
        setStringPainted(true);
    }

    /**
     * Gets a new or old border for this color.
     */
    public Border getCachedOrNewBorder(Color c) {
        if (c == null)
            return null;
        if (borders == null)
            return null;

        Border b = borders.get(c);
        if (b == null) {
            b = BorderFactory.createMatteBorder(2, 5, 2, 5, c);
            borders.put(c, b);
        }
        return b;
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
    protected String getDescription(Object value) {
        return Integer.toString(getBarStatus(value)) + " %";
    }

    /**
     * @param value the same value that initializes the cell
     * @return what the progress bar component should be set to
     */
    protected int getBarStatus(Object value) {
        return value == null ? 0 : ((Integer) value).intValue();
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
        if (propertyName == "text") {
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
