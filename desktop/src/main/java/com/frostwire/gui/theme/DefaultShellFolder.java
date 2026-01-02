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

package com.frostwire.gui.theme;

import java.io.File;

/**
 * @author Michael Martak
 * @since 1.4
 */
class DefaultShellFolder extends ShellFolder {
    /**
     * Create a file system shell folder from a file
     */
    DefaultShellFolder(ShellFolder parent, File f) {
        super(parent, f.getAbsolutePath());
    }

    /**
     * This method is implemented to make sure that no instances
     * of <code>ShellFolder</code> are ever serialized. An instance of
     * this default implementation can always be represented with a
     * <code>java.io.File</code> object instead.
     *
     * @returns a <code>java.io.File</code> replacement object.
     */
    protected Object writeReplace() {
        return new File(getPath());
    }

    /**
     * @return An array of shell folders that are children of this shell folder
     * object, null if this shell folder is empty.
     */
    public File[] listFiles() {
        File[] files = super.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                files[i] = new DefaultShellFolder(this, files[i]);
            }
        }
        return files;
    }

    /**
     * @return Whether this shell folder is a link
     */
    public boolean isLink() {
        return false; // Not supported by default
    }

    /**
     * @return Whether this shell folder is marked as hidden
     */
    public boolean isHidden() {
        String fileName = getName();
        if (fileName.length() > 0) {
            return (fileName.charAt(0) == '.');
        }
        return false;
    }

    /**
     * @return The name used to display this shell folder
     */
    public String getDisplayName() {
        return getName();
    }
}
