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
import javax.swing.table.TableColumn;

/**
 * A personalized TableColumn for storing extended information.
 * This class provides support for storing:
 * <ul>
 * <li> The model number of the column
 *      (this doubles as the default order number) </li>
 * <li> The messageBundle ID of the column </li>
 * <li> The default width of the column
 *      (as opposed to the current preferredSize) </li>
 * <li> The default visibility of the column
 *      (as opposed to the current visibility) </li>
 * <li> The class of this column </li>
 * </ul>
 */
public class LimeTableColumn extends TableColumn {
    /**
     * Variable for the HeaderRenderer for all components.
     */
    private static TableCellRenderer HEADER_RENDERER;
    /**
     * Variable for an invisible HeaderRenderer.
     */
    private static TableCellRenderer INVIS_RENDERER;
    private final boolean defaultVisibility;
    private final int defaultWidth;
    private final String messageId;
    private final String name;
    private final Icon icon;
    private final boolean visName;
    private final Class<?> clazz;
    private final boolean initialized;

    /**
     * Creates a new column.
     */
    public LimeTableColumn(int model, final String id, final String name, int width, boolean vis, Class<?> clazz) {
        this(model, id, name, null, width, vis, clazz);
    }

    public LimeTableColumn(int model, final String id, final String name, int width, boolean vis, boolean visName, boolean resizable, Class<?> clazz) {
        this(model, id, name, null, width, vis, visName, clazz);
        setResizable(resizable);
        if (!resizable) {
            setMaxWidth(width);
            setMinWidth(width);
        }
    }

    public LimeTableColumn(int model, final String id, final String name, int width, boolean vis, boolean visName, Class<?> clazz) {
        this(model, id, name, null, width, vis, visName, clazz);
    }

    /**
     * Creates a new column.
     */
    private LimeTableColumn(int model, final String id, final String name, final Icon icon, int width, boolean vis, boolean visName, Class<?> clazz) {
        super(model);
        initialized = true;
        this.defaultVisibility = vis;
        this.defaultWidth = width;
        if (defaultWidth != -1)
            super.setPreferredWidth(width);
        this.messageId = id;
        super.setIdentifier(id);
        this.name = name;
        this.icon = icon;
        this.visName = visName;
        this.clazz = clazz;
        setHeaderVisible(true);
    }

    public LimeTableColumn(int model, final String id, final String name, final Icon icon, int width, boolean vis, Class<?> clazz) {
        this(model, id, name, icon, width, vis, true, clazz);
    }

    public String toString() {
        return messageId;
    }

    /**
     * Sets the visibility of the header.
     * <p>
     * Returns this so that it can be used easily for assigning
     * variables.
     */
    private void setHeaderVisible(boolean vis) {
        if (vis) {
            super.setHeaderRenderer(getHeaderSortRenderer());
            if (visName) {
                if (icon != null) {
                    super.setHeaderValue(icon);
                } else //noinspection ReplaceNullCheck
                    if (name != null) {
                    super.setHeaderValue(name);
                } else {
                    super.setHeaderValue("");
                }
            } else {
                super.setHeaderValue("");
            }
        } else {
            super.setHeaderRenderer(getInvisSortRenderer());
            super.setHeaderValue("");
        }
    }

    /**
     * Gets the default visibility for this column.
     */
    public boolean getDefaultVisibility() {
        return defaultVisibility;
    }

    /**
     * Gets the default width for this column.
     */
    public int getDefaultWidth() {
        return defaultWidth;
    }

    /**
     * Gets the default order for this column.
     */
    public int getDefaultOrder() {
        return getModelIndex();
    }

    /**
     * Get the name, as a string.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the Icon.
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Gets the class of this column.
     */
    Class<?> getColumnClass() {
        return clazz;
    }

    /**
     * Gets the Id as a string.
     */
    public String getId() {
        return messageId;
    }

    /*
      The following methods are overridden to ensure that we never
      accidentally change the default values.  This is absolutely
      necessary so that the DefaultColumnPreferenceHandler can correctly
      write the default values to the settings.
     */

    /**
     * Disallows changing of model number
     */
    public void setModelIndex(int idx) {
        if (!initialized)
            return;
        throw new IllegalStateException("cannot change model index");
    }

    /**
     * Disallows changing of header value
     */
    public void setHeaderValue(Object val) {
        if (!initialized)
            return;
        throw new IllegalStateException("cannot change header value");
    }

    /**
     * Disallows changing of identifier
     */
    public void setIdentifier(Object id) {
        if (!initialized)
            return;
        throw new IllegalStateException("cannot change id");
    }

    private TableCellRenderer getHeaderSortRenderer() {
        if (HEADER_RENDERER == null) {
            HEADER_RENDERER = new SortHeaderRenderer();
        }
        return HEADER_RENDERER;
    }

    private TableCellRenderer getInvisSortRenderer() {
        if (INVIS_RENDERER == null) {
            SortHeaderRenderer rnd = new SortHeaderRenderer();
            rnd.setAllowIcon();
            INVIS_RENDERER = rnd;
        }
        return INVIS_RENDERER;
    }
}
