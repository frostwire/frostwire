/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.shell;

/**
 * A registration in the platform shell that sets a program as the default viewer for a protocol link or file type.
 */
interface ShellAssociation {
    /**
     * @return true if we are currently handling this association
     */
    boolean isRegistered();

    /**
     * @return true if nobody is handling this association
     */
    boolean isAvailable();

    /**
     * Associates this running program with this protocol or file type in the shell.
     */
    void register();

    /**
     * Clears this shell association, leaving no program registered.
     */
    void unregister();
}