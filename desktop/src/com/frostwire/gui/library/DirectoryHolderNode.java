/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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
package com.frostwire.gui.library;

public class DirectoryHolderNode extends LibraryNode {
    private static final long serialVersionUID = 8351386662518599629L;
    private final DirectoryHolder directoryHolder;

    public DirectoryHolderNode(DirectoryHolder directoryHolder) {
        super(directoryHolder.getName());
        this.directoryHolder = directoryHolder;
    }

    public DirectoryHolder getDirectoryHolder() {
        return directoryHolder;
    }
}
