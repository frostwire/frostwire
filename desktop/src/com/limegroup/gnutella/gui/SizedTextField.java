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

package com.limegroup.gnutella.gui;

import java.awt.Dimension;

import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;

/**
 * This class creates a <tt>JTextField</tt> with a standardized size.<p>
 *
 * It sets the preffered and maximum size of the field to the standard
 * <tt>Dimension</tt> or sets the preferred and maximum sizes to the
 * <tt>Dimension</tt> argument.
 */
public final class SizedTextField extends LimeTextField {
	
	/**
	 * Constant for the standard height for <tt>JTextField</tt>.
	 */
	public static final int STANDARD_HEIGHT = 28;	
	
	/**
	 * Constant for the standard <tt>Dimension</tt> for <tt>JTextField</tt>.
	 */
	public static final Dimension STANDARD_DIMENSION = new Dimension(500, STANDARD_HEIGHT);

	public static final Dimension RESTRICT_HEIGHT_DIMENSION = new Dimension(Integer.MAX_VALUE, STANDARD_HEIGHT);
	
	/**
	 * Creates a <tt>JTextField</tt> with a standard size.
	 */
	public SizedTextField() {
		setPreferredSize(STANDARD_DIMENSION);
		setMaximumSize(STANDARD_DIMENSION);
	}

	/**
	 * Creates a <tt>JTextField</tt> with a standard size and with the 
	 * specified <tt>Dimension</tt>.
	 *
	 * @param dim the <tt>Dimension</tt> to size the field to
	 */
	public SizedTextField(final Dimension dim) {
		setPreferredSize(dim);
		setMaximumSize(dim);
	}

	/**
	 * Creates a <tt>JTextField</tt> with a standard size and with the 
	 * specified number of columns.
	 *
	 * @param columns the number of columns to use in the field
	 */
	public SizedTextField(final int columns) {
		super(columns);
		setPreferredSize(STANDARD_DIMENSION);
		setMaximumSize(STANDARD_DIMENSION);
	}

	/**
	 * Creates a <tt>JTextField</tt> with a standard size and with the 
	 * specified number of columns and the specified <tt>Dimension</tt>..
	 *
	 * @param columns the number of columns to use in the field
	 * @param dim the <tt>Dimension</tt> to size the field to
	 */
	public SizedTextField(final int columns, final Dimension dim) {
		super(columns);
		setPreferredSize(dim);
		setMaximumSize(dim);
	}
	
	/**
	 * Creates a <tt>JTextField</tt> with a standard size and with the 
	 * specified number of columns.
	 *
	 * @param columns the number of columns to use in the field
	 */
	public SizedTextField(final int columns, final SizePolicy sizePolicy) {
		super(columns);

		GUIUtils.restrictSize(this, sizePolicy);
	}
}
