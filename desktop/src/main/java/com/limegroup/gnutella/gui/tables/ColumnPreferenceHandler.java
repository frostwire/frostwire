/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.tables;

/**
 * Encapsulates access to preferences for a table's column.
 */
public interface ColumnPreferenceHandler {
    /**
     * Reverts this table's header preferences to their default
     * values.
     */
    void revertToDefault();

    /**
     * Determines whether or not the columns are already their default values.
     */
    boolean isDefault();

    /**
     * Sets the headers to the correct widths, depending on
     * the user's preferences for this table.
     */
    void setWidths();

    /**
     * Sets the headers to the correct order, depending on the
     * user's preferences for this table.
     */
    void setOrder();

    /**
     * Sets the headers so that some may be invisible, depending
     * on the user's preferences for this table.
     */
    void setVisibility();

    /**
     * Sets the single SimpleColumnListener callback.
     */
    @SuppressWarnings("unused")
    void setSimpleColumnListener(SimpleColumnListener scl);
}