/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;

import java.awt.*;

/**
 * A <code>WholeNumberField</code> with a standard size.
 */
public class SizedWholeNumberField extends WholeNumberField {
    /**
     * Constant for the standard number of columns in the field.
     */
    private static final int STANDARD_COLUMNS = 5;
    /**
     * constant dimension for sizing number fields.
     */
    private final Dimension STANDARD_DIMENSION = new Dimension(5, 28);

    /**
     * Constructs a <code>WholeNumberField</code> with a standard size.
     */
    public SizedWholeNumberField() {
        super(STANDARD_COLUMNS);
        setPreferredSize(STANDARD_DIMENSION);
        setMaximumSize(STANDARD_DIMENSION);
    }

    /**
     * Constructs a <code>WholeNumberField</code> with a standard size.
     * This constructor allows the number of columns to be customized.
     *
     * @param columns the number of columns to use
     */
    public SizedWholeNumberField(int columns) {
        super(columns);
        setPreferredSize(STANDARD_DIMENSION);
        setMaximumSize(STANDARD_DIMENSION);
    }

    public SizedWholeNumberField(int value, int columns, final SizePolicy sizePolicy) {
        super(value, columns);
        GUIUtils.restrictSize(this, sizePolicy);
    }
}
