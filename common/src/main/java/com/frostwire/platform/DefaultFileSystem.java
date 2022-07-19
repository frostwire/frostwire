/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
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
            LOG.info("Success: DefaultFileSystem.copy(src=" + src + ", dest=" + dest + ")");
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
    public void walk(File file, FileFilter filter) {
        walkFiles(Platforms.fileSystem(), file, filter);
    }
}
