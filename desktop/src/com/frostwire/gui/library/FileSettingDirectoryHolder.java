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

import org.limewire.setting.FileSetting;



/**
 * Implementation of the {@link DirectoryHolder} interface backed by a file 
 * setting.
 */
public class FileSettingDirectoryHolder extends AbstractDirectoryHolder {

	private String name;
	private String desc;
	private FileSetting fs;
	
	public FileSettingDirectoryHolder(FileSetting fs, String name, String description) {
		this.name = name;
		this.fs = fs;
		this.desc = description;
	}
	
	public FileSettingDirectoryHolder(FileSetting fs, String name) {
		this(fs, name, null);
	}
	
	public FileSettingDirectoryHolder(FileSetting fs) {
		this(fs, null);
	}
	
	/**
	 * Returns the name of the directory if no name is set in the constructor.
	 */
	public String getName() {
		return name != null ? name : getDirectory().getName();
	}

	/**
	 * Returns the absolute path of directory if none is provided in the
	 * constructor.
	 */
	public String getDescription() {
		return desc != null ? desc : getDirectory().getAbsolutePath();
	}

	public File getDirectory() {
		return fs.getValue();
	}
}
