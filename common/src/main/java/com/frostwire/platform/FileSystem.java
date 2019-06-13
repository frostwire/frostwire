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

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public interface FileSystem {
    boolean isDirectory(File file);

    boolean isFile(File file);

    boolean canRead(File file);

    boolean canWrite(File file);

    long length(File file);

    long lastModified(File file);

    boolean exists(File file);

    boolean mkdirs(File file);

    boolean delete(File file);

    File[] listFiles(File file, FileFilter filter);

    boolean copy(File src, File dest);

    boolean write(File file, byte[] data);

    /**
     * This should instruct the underlying operating system
     * that a new file is in place, it could be a simple
     * notification for update in database or media scan like
     * in android.
     *
     * @param file the file to scan.
     */
    void scan(File file);

    void walk(File file, FileFilter filter);
}
