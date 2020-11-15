/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.library;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;

/**
 * Interface for the directory data model behind a node in the library tree.
 */
public interface DirectoryHolder extends FileFilter {
    /**
     * @return Returns the name of the directory.
     */
    String getName();

    /**
     * @return Returns an additional description which is displayed as a tooltip.
     */
    String getDescription();

    /**
     * @return Returns the physical directory behind this virtual directory holder.
     */
    File getDirectory();

    /**
     * @return Returns the files that should be displayed when this directory holder
     * is selected.
     */
    File[] getFiles();

    /**
     * @return Returns the number of files that this directory holder contains.
     */
    int size();

    /**
     * @return Returns a display item for the folder.
     */
    Icon getIcon();
}