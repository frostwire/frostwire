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

package com.frostwire.util.filetypes;

/**
 * The <code>AssociationException</code> class and its subclasses are thrown by
 * certain methods of <code>AssociationService</code> to indicate the operation
 * fails.
 *
 * @see AssociationService
 */
public class AssociationException extends Exception {

    /**
     * Constructs an <code>AssociationException</code> object with no detail message.
     */
    AssociationException() {
        super();
    }

    /**
     * Constructs an <code>AssociationException</code> object with the specified
     * detail message.
     *
     * @param msg the detail message pertaining to this exception.
     */
    AssociationException(String msg) {
        super(msg);
    }
}
