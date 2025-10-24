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
 * This class creates a `JTextField` with a standardized size.<p>
 * <p>
 * It sets the preffered and maximum size of the field to the standard
 * `Dimension` or sets the preferred and maximum sizes to the
 * `Dimension` argument.
 */
public final class SizedTextField extends LimeTextField {
    /**
     * Constant for the standard height for `JTextField`.
     */
    private static final int STANDARD_HEIGHT = 28;
    /**
     * Constant for the standard `Dimension` for `JTextField`.
     */
    private static final Dimension STANDARD_DIMENSION = new Dimension(500, STANDARD_HEIGHT);

    /**
     * Creates a `JTextField` with a standard size.
     */
    SizedTextField() {
        setPreferredSize(STANDARD_DIMENSION);
        setMaximumSize(STANDARD_DIMENSION);
    }

    /**
     * Creates a `JTextField` with a standard size and with the
     * specified number of columns.
     *
     * @param columns the number of columns to use in the field
     */
    public SizedTextField(final int columns, final SizePolicy sizePolicy) {
        super(columns);
        GUIUtils.restrictSize(this, sizePolicy);
    }
}