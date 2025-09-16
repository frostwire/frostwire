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

package com.frostwire.gui.library;

import com.frostwire.gui.bittorrent.TorrentUtil;

import javax.swing.*;
import java.io.File;
import java.util.Set;

/**
 * Abstract implementation of the DirectoryHolder interface, providing a filtered
 * way for listing the files in the directory.
 */
public abstract class AbstractDirectoryHolder implements DirectoryHolder {
    Set<File> _hideFiles;

    /**
     * Uses the file filter for listing the files in the directory provided by
     * {@link #getDirectory}.
     */
    public File[] getFiles() {
        _hideFiles = TorrentUtil.getIgnorableFiles();
        File[] files = getDirectory().listFiles(this);
        return (files != null) ? files : new File[0];
    }

    public boolean accept(File file) {
        if (_hideFiles != null && (_hideFiles.contains(file) || !isFileVisible(file)
                || file.getName().toLowerCase().equals(".ds_store")
                || isPartsFile(file))) {
            return false;
        }
        File parent = file.getParentFile();
        return parent != null && parent.equals(getDirectory());
    }

    private boolean isPartsFile(File f) {
        return f.getName().startsWith(".") && f.getName().endsWith(".parts");
    }

    /**
     * Returns true if the given file is visible
     */
    private boolean isFileVisible(File file) {
        return file != null && file.exists() && file.canRead() && !file.isHidden();
    }

    public String getName() {
        return getDirectory().getName();
    }

    public String getDescription() {
        return getDirectory().getAbsolutePath();
    }

    /**
     * Returns the number of files that this directory holder contains.
     */
    public int size() {
        File[] files = getFiles();
        if (files == null)
            return 0;
        return files.length;
    }

    public Icon getIcon() {
        return null;
    }
}
