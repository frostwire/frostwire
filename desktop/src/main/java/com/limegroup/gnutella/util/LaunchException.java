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

package com.limegroup.gnutella.util;

import java.io.IOException;

/**
 * Signals that an exception occurred during execution of a command.
 */
public class LaunchException extends IOException {
    private static final long serialVersionUID = -3994751041116114570L;
    private final String[] command;

    /**
     * @param cause   the exception that occurred during execution of command
     * @param command the executed command
     */
    LaunchException(IOException cause, String... command) {
        this.command = command;
        initCause(cause);
    }

    /**
     * @param command the executed command
     */
    public LaunchException(String... command) {
        this.command = command;
    }

    public String[] getCommand() {
        return command;
    }
}
