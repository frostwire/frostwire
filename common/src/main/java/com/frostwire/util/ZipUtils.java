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

package com.frostwire.util;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ZipUtils {
    private static final Logger LOG = Logger.getLogger(ZipUtils.class);

    public static boolean unzip(File zipFile, File outputDir) {
        return unzip(zipFile, outputDir, null);
    }

    public static boolean unzip(File zipFile, File outputDir, ZipListener listener) {
        boolean result = false;
        try {
            FileUtils.deleteDirectory(outputDir);
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                unzipEntries(outputDir, zis, getItemCount(zipFile), System.currentTimeMillis(), listener);
            }
            result = true;
        } catch (IOException e) {
            LOG.error("Unable to uncompress " + zipFile + " to " + outputDir, e);
            result = false;
        }
        return result;
    }

    private static void unzipEntries(File folder, ZipInputStream zis, int itemCount, long time, ZipListener listener) throws IOException {
        ZipEntry ze = null;
        int item = 0;
        while ((ze = zis.getNextEntry()) != null) {
            item++;
            String fileName = ze.getName();
            File newFile = new File(folder, fileName);
            LOG.debug("unzip: " + newFile.getAbsoluteFile());
            if (ze.isDirectory()) {
                if (!newFile.mkdirs()) {
                    break;
                }
                continue;
            }
            if (listener != null) {
                int progress = (item == itemCount) ? 100 : (int) (((double) (item * 100)) / (double) (itemCount));
                listener.onUnzipping(fileName, progress);
            }
            try (FileOutputStream fos = new FileOutputStream(newFile)) {
                int n;
                byte[] buffer = new byte[1024];
                while ((n = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, n);
                    if (listener != null && listener.isCanceled()) { // not the best way
                        throw new IOException("Uncompress operation cancelled");
                    }
                }
            } finally {
                zis.closeEntry();
            }
            newFile.setLastModified(time);
        }
    }

    private static int getItemCount(File file) throws IOException {
        ZipFile zip = null;
        int count = 0;
        try {
            zip = new ZipFile(file);
            count = zip.size();
        } finally {
            try {
                zip.close();
            } catch (Throwable e) {
                // ignore
            }
        }
        return count;
    }

    public interface ZipListener {
        void onUnzipping(String fileName, int progress);

        boolean isCanceled();
    }
}
