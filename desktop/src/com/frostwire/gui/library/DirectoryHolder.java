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
import java.io.FileFilter;

import javax.swing.Icon;

/**
 * Interface for the directory data model behind a node in the library tree.
 * 
 */
public interface DirectoryHolder extends FileFilter {

	/**
	 * Returns the name of the directory.
	 * @return
	 */
	String getName();
	/**
	 * Returns an additional description which is displayed as a tooltip.
	 * @return
	 */
	String getDescription();
	/**
	 * Returns the physical directory behind this virtual directory holder.
	 * @return
	 */
	File getDirectory();
	/**
	 * Returns the files that should be displayed when this directory holder
	 * is selected.
	 * @return
	 */
	File[] getFiles();
	/**
	 * Returns the number of files that this directory holder contains.
	 */
	int size();
	/**
	 * Returns a display item for the folder.
	 * @return
	 */
	Icon getIcon();
	
	/**
	 * Determines if this is empty.
	 */
	boolean isEmpty();
}