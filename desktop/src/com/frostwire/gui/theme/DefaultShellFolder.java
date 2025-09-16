/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
