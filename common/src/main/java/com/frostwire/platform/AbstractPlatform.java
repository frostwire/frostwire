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

package com.frostwire.platform;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractPlatform implements Platform {

    private final FileSystem fileSystem;
    private final SystemPaths systemPaths;

    public AbstractPlatform(FileSystem fileSystem, SystemPaths systemPaths) {
        if (fileSystem == null) {
            throw new IllegalArgumentException("FileSystem can't be null");
        }
        if (systemPaths == null) {
            throw new IllegalArgumentException("SystemPaths can't be null");
        }

        this.fileSystem = fileSystem;
        this.systemPaths = systemPaths;
    }

    public FileSystem fileSystem() {
        return fileSystem;
    }

    @Override
    public SystemPaths systemPaths() {
        return systemPaths;
    }

    @Override
    public boolean android() {
        return false;
    }

    @Override
    public int androidVersion() {
        return -1;
    }

    @Override
    public boolean experimental() {
        return false;
    }
}
