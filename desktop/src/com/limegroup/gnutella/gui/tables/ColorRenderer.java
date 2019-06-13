/*
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

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws a cell with it's default renderer, but the foreground
 * colored differently.
 * <p>
 * Due to the nature of renderer components being shared
 * between cells, this can not act directly on the renderer
 * that's returned.  Otherwise, this has the side effect of altering
 * future renderings that aren't necesarily from a ColoredCell.
 * To still allow complete functionality of the
 * ColorRenderer to work regardless of the cell's specific renderer,
 * this will instantiate a copy of that renderer and cache it for future
 * use.
 * <p>
 * ******************************************************************
 * This requires that any cell that is being colored have a default
 * TableCellRenderer that has a parameterless constructor.
 * ******************************************************************
 * <p>
 * New renderers are created flyweight-style.
 * <p>
 * All useful calls are wrapped and redirected to the underlying renderer.
 * <p>
 * This class takes advantage of the potential of the 'renderer' component
 * to not be the TableCellRenderer itself.  It is merely by convention
 * that all getTableCellRendererComponent calls return 'this'.
 * However, there is nothing that specifically states the TableCellRenderer
 * does not delegate to other renderers.  Unfortunately, the JTable
 * sends UI updates to the TableCellRenderer, and not the component that
 * does the renderering.  TableCellRenderer is actually a rather poor name,
 * or more precisely, the function 'getTableCellRendererComponent' is a
 * poor name for being in an interface titled TableCellRenderer.
 * The delegate functionality is useful, but so is the rubber-stamping.
 * It would probably have been better to name getTableCel.. 'stamp'.
 * <p>
 * This class does *NOT* instantiate new copies of the component returned
 * from getTableCellRendererComponent.  It needs new copies of the
 * TableCellRenderer, in order to call further getTableCellRendererComponents.
 * Since the two are generally the same, it works out nicely.
 * <p>
 * UI Updates are propagated to all contained TableCellRenderers.
 * <p>
 * For clarity, this class just extends JComponent (needed to recieve
 * an updateUI call).  It is not really a component -- it just delegates.
 * <p>
 * NOTE: This does not color selected or focused cells.
 */
class ColorRenderer extends DefaultTableBevelledCellRenderer {
    /**
     * Map is from TableCellRenderer to TableCellRenderer.
     * Every instance of a renderer will have a mirrored instance as its value.
     */
    private static final Map<TableCellRenderer, TableCellRenderer> otherRenderers = new HashMap<>();

    public ColorRenderer() {
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSel, boolean hasFocus, int row, int column) {
        ColoredCell cc = (ColoredCell) value;
        Color clr;
        Object val;
        Class<?> clazz;
        if (cc != null) {
            clr = cc.getColor();
            val = cc.getValue();
            clazz = cc.getCellClass();
        } else {
            clr = null;
            val = "";
            clazz = String.class;
        }
        TableCellRenderer tcr = table.getDefaultRenderer(clazz);
        tcr = getCachedOrNewRenderer(tcr);
        Component renderer = tcr.getTableCellRendererComponent(table, val, isSel, hasFocus, row, column);
        if ((!isSel && !hasFocus)) // || isReadable(clr, renderer.getBackground()))
            renderer.setForeground(clr);
        return renderer;
    }

    public void updateUI() {
        for (Object o : otherRenderers.values()) {
            if (o instanceof JComponent) {
                ((JComponent) o).updateUI();
            }
        }
    }

    private TableCellRenderer getCachedOrNewRenderer(TableCellRenderer tcr) {
        TableCellRenderer renderer = otherRenderers.get(tcr);
        // if it doesn't exist, put a copy of the renderer in there
        // so that the setForeground doesn't effect the real renderer.
        if (renderer == null) {
            Class<? extends TableCellRenderer> rendererClass = tcr.getClass();
            try {
                renderer = rendererClass.getDeclaredConstructor().newInstance();
            } catch (IllegalAccessException | InstantiationException | ClassCastException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            otherRenderers.put(tcr, renderer);
        }
        return renderer;
    }
}