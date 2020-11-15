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

import com.frostwire.util.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public class DefaultFileSystem implements FileSystem {
    private static final Logger LOG = Logger.getLogger(DefaultFileSystem.class);

    public static void walkFiles(FileSystem fs, File file, FileFilter filter) {
        File[] arr = fs.listFiles(file, filter);
        if (arr == null) {
            return;
        }
        Deque<File> q = new LinkedList<>(Arrays.asList(arr));
        while (!q.isEmpty()) {
            File child = q.pollFirst();
            filter.file(child);
            if (fs.isDirectory(child)) {
                arr = fs.listFiles(child, filter);
                if (arr != null) {
                    for (int i = arr.length - 1; i >= 0; i--) {
                        q.addFirst(arr[i]);
                    }
                }
            }
        }
    }

    @Override
    public boolean isDirectory(File file) {
        return file.isDirectory();
    }

    @Override
    public boolean isFile(File file) {
        return file.isFile();
    }

    @Override
    public boolean canRead(File file) {
        return file.canRead();
    }

    @Override
    public boolean canWrite(File file) {
        return file.canWrite();
    }

    @Override
    public long length(File file) {
        return file.length();
    }

    @Override
    public long lastModified(File file) {
        return file.lastModified();
    }

    @Override
    public boolean exists(File file) {
        return file.exists();
    }

    @Override
    public boolean mkdirs(File file) {
        return file.mkdirs();
    }

    @Override
    public boolean delete(File file) {
        return file.delete();
    }

    @Override
    public File[] listFiles(File file, FileFilter filter) {
        return file.listFiles(filter);
    }

    @Override
    public boolean copy(File src, File dest) {
        try {
            FileUtils.copyFile(src, dest);
            return true;
        } catch (Throwable e) {
            LOG.error("Error in copy file: " + src + " -> " + dest, e);
        }
        return false;
    }

    @Override
    public boolean write(File file, byte[] data) {
        try {
            FileUtils.writeByteArrayToFile(file, data);
            return true;
        } catch (Throwable e) {
            LOG.error("Error in writing to file: " + file, e);
        }
        return false;
    }

    @Override
    public void scan(File file) {
        // LOG.warn("Scan of file not implemented");
    }

    @Override
    public void walk(File file, FileFilter filter) {
        walkFiles(Platforms.fileSystem(), file, filter);
    }
}
