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

package com.frostwire.util.filetypes;

/**
 * An <code>AssociationAlreadyRegisteredException</code> is thrown by certain methods
 * of <code>AssociationService</code> while trying to add an association already
 * existed in the system.
 *
 * @see AssociationService
 */
public class AssociationAlreadyRegisteredException extends AssociationException {
    /**
     * Constructs an <code>AssociationAlreadyRegisteredException</code> object
     * with the specified detail message.
     *
     * @param msg the detail message pertaining to this exception.
     */
    AssociationAlreadyRegisteredException(String msg) {
        super(msg);
    }
}
