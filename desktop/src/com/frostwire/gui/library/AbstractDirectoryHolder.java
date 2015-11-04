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

import java.io.File;
import java.util.Set;

import javax.swing.Icon;

import com.frostwire.gui.bittorrent.TorrentUtil;

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
		if (_hideFiles!=null && (_hideFiles.contains(file) || !isFileVisible(file)
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
    protected boolean isFileVisible(File file) {
        if (file == null || !file.exists() || !file.canRead() || file.isHidden()) {
            return false;
        }

        return true;
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

    public boolean isEmpty() {
        return size() == 0;
    }
}
